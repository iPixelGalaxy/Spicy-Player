import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ContentView: View {
    private enum ImportTarget {
        case audio
        case folder
        case lyrics
    }

    private enum AppTab: Hashable {
        case home
        case library
        case nowPlaying
    }

    @StateObject private var viewModel = PlayerViewModel()
    @State private var isImporterPresented = false
    @State private var activeImportTarget: ImportTarget?
    @State private var selectedTab: AppTab = .home

    private var displayedTimeMs: Int {
        max(0, viewModel.currentTimeMs + viewModel.lyricOffsetMs)
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            AnimatedArtworkBackground(artwork: viewModel.artwork)

            TabView(selection: $selectedTab) {
                homePage
                    .tag(AppTab.home)
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }

                libraryPage
                    .tag(AppTab.library)
                    .tabItem {
                        Label("Library", systemImage: "music.note.list")
                    }

                nowPlayingPage
                    .tag(AppTab.nowPlaying)
                    .tabItem {
                        Label("Now Playing", systemImage: "quote.bubble.fill")
                    }
            }
            .tint(.white)
            .toolbarBackground(.visible, for: .tabBar)
            .toolbarBackground(.ultraThinMaterial, for: .tabBar)
            .safeAreaInset(edge: .bottom) {
                if viewModel.selectedTrackID != nil {
                    miniPlayerBar
                        .padding(.horizontal, 14)
                        .padding(.bottom, 58)
                }
            }

            importMenu
                .padding(.top, 14)
                .padding(.trailing, 18)
        }
        .fileImporter(
            isPresented: $isImporterPresented,
            allowedContentTypes: allowedContentTypes(),
            allowsMultipleSelection: false
        ) { result in
            let target = activeImportTarget
            activeImportTarget = nil

            switch result {
            case .success(let urls):
                guard let url = urls.first else {
                    return
                }

                Task {
                    switch target {
                    case .audio:
                        await viewModel.importSong(from: url)
                        selectedTab = .nowPlaying
                    case .folder:
                        await viewModel.importFolder(from: url)
                        selectedTab = .library
                    case .lyrics:
                        await viewModel.importLyricsForCurrentTrack(from: url)
                        selectedTab = .nowPlaying
                    case nil:
                        break
                    }
                }
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private var homePage: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                topBar
                heroPanel
                homeQuickActions

                if !viewModel.libraryTracks.isEmpty {
                    libraryPanel
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 24)
            .padding(.bottom, 120)
        }
    }

    private var libraryPage: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Text("Library")
                        .font(.system(size: 38, weight: .bold))
                        .foregroundStyle(.white)

                    Spacer()

                    Button {
                        Task {
                            await viewModel.shufflePlay()
                            selectedTab = .nowPlaying
                        }
                    } label: {
                        Label("Shuffle", systemImage: "shuffle")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .background(.ultraThinMaterial, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }

                Text("\(viewModel.libraryTracks.count) saved tracks")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white.opacity(0.56))

                LazyVStack(spacing: 12) {
                    ForEach(viewModel.libraryTracks) { track in
                        Button {
                            Task {
                                await viewModel.loadTrack(track, autoplay: true)
                                selectedTab = .nowPlaying
                            }
                        } label: {
                            LibraryListRow(track: track, isActive: viewModel.selectedTrackID == track.id)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 24)
            .padding(.bottom, 120)
        }
    }

    private var nowPlayingPage: some View {
        VStack(spacing: 16) {
            topBar
            heroPanel
            syncSection
            lyricsPanel
            controls
        }
        .padding(.horizontal, 18)
        .padding(.top, 24)
        .padding(.bottom, 110)
    }

    private var topBar: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Spicy Player")
                    .font(.system(size: 36, weight: .semibold))
                    .foregroundStyle(.white)

                Text(viewModel.lyricsStatus)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(2)

                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Color(red: 1.0, green: 0.72, blue: 0.67))
                        .lineLimit(3)
                }
            }

            Spacer(minLength: 0)
        }
    }

    private var importMenu: some View {
        Menu {
            Button {
                activeImportTarget = .audio
                isImporterPresented = true
            } label: {
                Label("Import Song", systemImage: "music.note")
            }

            Button {
                activeImportTarget = .folder
                isImporterPresented = true
            } label: {
                Label("Import Folder", systemImage: "folder.badge.plus")
            }

            Button {
                activeImportTarget = .lyrics
                isImporterPresented = true
            } label: {
                Label("Attach Lyrics", systemImage: "quote.bubble")
            }
            .disabled(!viewModel.canAttachLyrics())
        } label: {
            ZStack {
                Circle()
                    .fill(.ultraThinMaterial)
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.white.opacity(0.26), Color.white.opacity(0.06)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                Circle()
                    .stroke(Color.white.opacity(0.18), lineWidth: 1)

                Image(systemName: "plus")
                    .font(.system(size: 19, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 54, height: 54)
            .shadow(color: .black.opacity(0.18), radius: 18, y: 8)
        }
    }

    private var homeQuickActions: some View {
        HStack(spacing: 12) {
            Button {
                Task {
                    await viewModel.shufflePlay()
                    selectedTab = .nowPlaying
                }
            } label: {
                LiquidActionTile(title: "Shuffle", systemName: "shuffle")
            }
            .buttonStyle(.plain)

            Button {
                selectedTab = .library
            } label: {
                LiquidActionTile(title: "Library", systemName: "square.stack.fill")
            }
            .buttonStyle(.plain)
        }
    }

    private var syncSection: some View {
        HStack(spacing: 10) {
            syncButton(title: "-100") {
                viewModel.adjustLyricOffset(by: -100)
            }

            syncButton(title: "Reset") {
                viewModel.resetLyricOffset()
            }

            syncButton(title: "+100") {
                viewModel.adjustLyricOffset(by: 100)
            }

            Spacer()

            Text("Sync \(viewModel.lyricOffsetMs >= 0 ? "+" : "")\(viewModel.lyricOffsetMs) ms")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white.opacity(0.68))
        }
    }

    private var heroPanel: some View {
        HStack(spacing: 16) {
            artworkView

            VStack(alignment: .leading, spacing: 8) {
                Text(viewModel.nowPlayingTitle)
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(2)

                Text(viewModel.libraryTracks.first(where: { $0.id == viewModel.selectedTrackID })?.hasLyrics == true ? "Synced TTML loaded" : "No paired lyrics yet")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white.opacity(0.64))

                Text(timecode(displayedTimeMs))
                    .font(.system(size: 14, weight: .medium, design: .monospaced))
                    .foregroundStyle(.white.opacity(0.72))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(.white.opacity(0.10), in: Capsule())
            }

            Spacer(minLength: 0)
        }
        .padding(18)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .stroke(.white.opacity(0.10), lineWidth: 1)
        )
    }

    private var miniPlayerBar: some View {
        HStack(spacing: 14) {
            compactArtworkView
                .onTapGesture {
                    selectedTab = .nowPlaying
                }

            VStack(alignment: .leading, spacing: 4) {
                Text(viewModel.nowPlayingTitle)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)

                Text(viewModel.lyricsStatus)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white.opacity(0.54))
                    .lineLimit(1)
            }

            Spacer(minLength: 0)

            Button(action: viewModel.togglePlayback) {
                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(.white.opacity(0.14), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(.white.opacity(0.10), lineWidth: 1)
        )
        .contentShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .onTapGesture {
            selectedTab = .nowPlaying
        }
    }

    private var compactArtworkView: some View {
        Group {
            if let artwork = viewModel.artwork {
                Image(uiImage: artwork)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.white.opacity(0.18), Color.white.opacity(0.06)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Image(systemName: "music.note")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(.white.opacity(0.78))
                }
            }
        }
        .frame(width: 52, height: 52)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var artworkView: some View {
        Group {
            if let artwork = viewModel.artwork {
                Image(uiImage: artwork)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    LinearGradient(
                        colors: [Color.white.opacity(0.18), Color.white.opacity(0.06)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                    Image(systemName: "music.note")
                        .font(.system(size: 30, weight: .medium))
                        .foregroundStyle(.white.opacity(0.78))
                }
            }
        }
        .frame(width: 108, height: 108)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.22), radius: 22, y: 10)
    }

    private var libraryPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Library")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))

                Spacer()

                Text("\(viewModel.libraryTracks.count) tracks")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(.white.opacity(0.52))
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(viewModel.libraryTracks) { track in
                        Button {
                            Task {
                                await viewModel.loadTrack(track, autoplay: true)
                            }
                        } label: {
                            LibraryTrackChip(
                                track: track,
                                isSelected: viewModel.selectedTrackID == track.id
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .padding(16)
        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var lyricsPanel: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 18) {
                    if viewModel.lines.isEmpty {
                        placeholderView
                    } else {
                        ForEach(viewModel.lines) { line in
                            LyricLineView(
                                line: line,
                                currentTimeMs: displayedTimeMs
                            )
                            .id(line.id)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                if !line.isInterlude && !line.isSongwriter {
                                    viewModel.seek(to: line.startMs)
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 26)
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .stroke(.white.opacity(0.08), lineWidth: 1)
            )
            .onChange(of: displayedTimeMs, initial: false) { _, _ in
                guard let activeID = viewModel.activeLineID(for: displayedTimeMs) else {
                    return
                }

                withAnimation(.spring(response: 0.52, dampingFraction: 0.88)) {
                    proxy.scrollTo(activeID, anchor: .center)
                }
            }
        }
    }

    private var placeholderView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Offline lyric playback")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)

            Text("Import songs or a full folder into the local library. TTML files are paired by basename, and you can still attach lyrics manually from the plus menu.")
                .font(.system(size: 16, weight: .regular))
                .foregroundStyle(.white.opacity(0.76))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var controls: some View {
        HStack(spacing: 18) {
            controlButton(systemName: viewModel.isShuffleEnabled ? "shuffle.circle.fill" : "shuffle") {
                viewModel.toggleShuffle()
            }

            controlButton(systemName: "backward.fill") {
                Task {
                    await viewModel.playPreviousTrack()
                }
            }

            Button(action: viewModel.togglePlayback) {
                ZStack {
                    Circle()
                        .fill(.white)
                    Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundStyle(.black)
                        .offset(x: viewModel.isPlaying ? 0 : 1)
                }
                .frame(width: 66, height: 66)
            }
            .buttonStyle(.plain)

            controlButton(systemName: "forward.fill") {
                Task {
                    await viewModel.playNextTrack()
                }
            }

            Spacer()

            Text(timecode(displayedTimeMs))
                .font(.system(size: 14, weight: .medium, design: .monospaced))
                .foregroundStyle(.white.opacity(0.82))
        }
        .padding(.horizontal, 8)
    }

    private func controlButton(systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(.white.opacity(0.12))
                Circle()
                    .stroke(.white.opacity(0.10), lineWidth: 1)
                Image(systemName: systemName)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
            }
            .frame(width: 52, height: 52)
        }
        .buttonStyle(.plain)
    }

    private func syncButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(.ultraThinMaterial, in: Capsule())
                .overlay(
                    Capsule()
                        .stroke(.white.opacity(0.08), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    private func allowedContentTypes() -> [UTType] {
        switch activeImportTarget {
        case .audio:
            return viewModel.supportedAudioTypes()
        case .folder:
            return viewModel.supportedFolderTypes()
        case .lyrics:
            return viewModel.supportedLyricsTypes()
        case nil:
            return []
        }
    }

    private func timecode(_ ms: Int) -> String {
        let totalSeconds = max(ms / 1000, 0)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

private struct LibraryTrackChip: View {
    let track: ImportedTrack
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(track.title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)

            Text(track.hasLyrics ? "Lyrics paired" : "No lyrics yet")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(track.hasLyrics ? Color(red: 1.0, green: 0.84, blue: 0.71) : .white.opacity(0.56))
                .lineLimit(1)
        }
        .frame(width: 170, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(background, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(isSelected ? .white.opacity(0.22) : .white.opacity(0.08), lineWidth: 1)
        )
    }

    private var background: some ShapeStyle {
        LinearGradient(
            colors: isSelected
                ? [Color.white.opacity(0.22), Color.white.opacity(0.10)]
                : [Color.white.opacity(0.10), Color.white.opacity(0.04)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}

private struct LiquidActionTile: View {
    let title: String
    let systemName: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: systemName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(.white)

            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .leading)
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        )
    }
}

private struct LibraryListRow: View {
    let track: ImportedTrack
    let isActive: Bool

    var body: some View {
        HStack(spacing: 14) {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.white.opacity(0.12))
                .frame(width: 54, height: 54)
                .overlay(
                    Image(systemName: track.hasLyrics ? "music.note.list" : "music.note")
                        .foregroundStyle(.white.opacity(0.76))
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(track.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)

                Text(track.hasLyrics ? "Synced TTML ready" : "Lyrics not attached")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white.opacity(0.54))
            }

            Spacer()

            if isActive {
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(.white.opacity(0.82))
            }
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .stroke(isActive ? .white.opacity(0.18) : .white.opacity(0.08), lineWidth: 1)
        )
    }
}

private struct LyricLineView: View {
    let line: LyricLine
    let currentTimeMs: Int

    private var isLineActive: Bool {
        line.startMs <= currentTimeMs && currentTimeMs <= line.endMs
    }

    private var isLinePast: Bool {
        currentTimeMs > line.endMs
    }

    var body: some View {
        Group {
            if line.isInterlude {
                InterludeView(line: line, currentTimeMs: currentTimeMs)
            } else {
                composedText()
                    .font(baseFont)
                    .frame(maxWidth: .infinity, alignment: line.oppositeAligned ? .trailing : .leading)
                    .multilineTextAlignment(line.oppositeAligned ? .trailing : .leading)
                    .opacity(lineOpacity)
                    .scaleEffect(isLineActive ? 1.02 : 1.0, anchor: line.oppositeAligned ? .trailing : .leading)
            }
        }
        .padding(.vertical, line.isInterlude ? 6 : 2)
        .animation(.easeOut(duration: 0.18), value: isLineActive)
    }

    private var baseFont: Font {
        if line.isSongwriter {
            return .system(size: 18, weight: .medium)
        }

        if line.isBackground {
            return .system(size: 25, weight: isLineActive ? .semibold : .regular)
        }

        return .system(size: 32, weight: isLineActive ? .semibold : .medium)
    }

    private var lineOpacity: Double {
        if isLineActive {
            return 1.0
        }
        if isLinePast {
            return 0.80
        }
        return line.isSongwriter ? 0.70 : 0.96
    }

    private func composedText() -> Text {
        if line.isSongwriter || line.words.isEmpty {
            return Text(line.displayText.isEmpty ? " " : line.displayText)
                .foregroundColor(staticLineColor())
        }

        return line.words.enumerated().reduce(Text("")) { partial, item in
            let word = item.element
            let prefix = item.offset == 0 || word.isPartOfWord ? "" : " "
            return partial + styledWord(prefix: prefix, word: word)
        }
    }

    private func styledWord(prefix: String, word: LyricWord) -> Text {
        let prefixText = Text(prefix).foregroundColor(upcomingWordColor())

        if word.isLetterGroup && word.startMs <= currentTimeMs && currentTimeMs <= word.endMs && !word.letters.isEmpty {
            let lettersText = word.letters.reduce(Text("")) { partial, letter in
                partial + Text(letter.char)
                    .foregroundColor(letterColor(letter))
                    .fontWeight(letterWeight(letter))
            }
            return prefixText + lettersText
        }

        return prefixText + Text(word.text)
            .foregroundColor(wordColor(word))
            .fontWeight(wordWeight(word))
    }

    private func staticLineColor() -> Color {
        if line.isSongwriter {
            return .white.opacity(0.72)
        }
        return .white
    }

    private func wordColor(_ word: LyricWord) -> Color {
        if currentTimeMs > word.endMs {
            return .white
        }

        if word.startMs <= currentTimeMs && currentTimeMs <= word.endMs {
            return Color(red: 1.0, green: 0.94, blue: 0.90)
        }

        return upcomingWordColor()
    }

    private func letterColor(_ letter: LyricLetter) -> Color {
        if currentTimeMs > letter.endMs {
            return .white
        }

        if letter.startMs <= currentTimeMs && currentTimeMs <= letter.endMs {
            return Color(red: 1.0, green: 0.94, blue: 0.90)
        }

        return upcomingWordColor()
    }

    private func wordWeight(_ word: LyricWord) -> Font.Weight {
        if word.startMs <= currentTimeMs && currentTimeMs <= word.endMs {
            return .semibold
        }
        if currentTimeMs > word.endMs {
            return .medium
        }
        return .regular
    }

    private func letterWeight(_ letter: LyricLetter) -> Font.Weight {
        if letter.startMs <= currentTimeMs && currentTimeMs <= letter.endMs {
            return .semibold
        }
        if currentTimeMs > letter.endMs {
            return .medium
        }
        return .regular
    }

    private func upcomingWordColor() -> Color {
        if line.isBackground {
            return .white.opacity(0.34)
        }
        return .white.opacity(0.42)
    }
}

private struct InterludeView: View {
    let line: LyricLine
    let currentTimeMs: Int

    var body: some View {
        let progress = interludeProgress()

        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                ForEach(0..<3, id: \.self) { index in
                    let phase = progress + (Double(index) * 0.18)
                    Circle()
                        .fill(Color.white.opacity(0.72))
                        .frame(width: 10, height: 10)
                        .scaleEffect(0.72 + 0.55 * pulse(phase))
                        .opacity(0.35 + 0.65 * pulse(phase))
                }
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(.white.opacity(0.10))

                    Capsule()
                        .fill(.white.opacity(0.72))
                        .frame(width: max(proxy.size.width * progress, 18))
                }
            }
            .frame(height: 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func interludeProgress() -> Double {
        let duration = max(Double(line.duration), 1)
        return max(0, min(Double(currentTimeMs - line.startMs) / duration, 1))
    }

    private func pulse(_ value: Double) -> Double {
        (sin(value * .pi * 4) + 1) / 2
    }
}

private struct AnimatedArtworkBackground: View {
    let artwork: UIImage?

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0)) { context in
            GeometryReader { proxy in
                let size = proxy.size
                let time = context.date.timeIntervalSinceReferenceDate

                ZStack {
                    LinearGradient(
                        colors: [Color(red: 0.08, green: 0.09, blue: 0.12), Color(red: 0.16, green: 0.12, blue: 0.14)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )

                    orb(
                        color: Color(red: 1.0, green: 0.54, blue: 0.45),
                        size: min(size.width, size.height) * 0.72,
                        x: size.width * (0.14 + 0.08 * sin(time * 0.35)),
                        y: size.height * (0.20 + 0.10 * cos(time * 0.28))
                    )

                    orb(
                        color: Color(red: 0.98, green: 0.82, blue: 0.63),
                        size: min(size.width, size.height) * 0.52,
                        x: size.width * (0.82 + 0.08 * cos(time * 0.26)),
                        y: size.height * (0.28 + 0.12 * sin(time * 0.31))
                    )

                    orb(
                        color: Color(red: 0.88, green: 0.36, blue: 0.28),
                        size: min(size.width, size.height) * 0.66,
                        x: size.width * (0.58 + 0.07 * sin(time * 0.20)),
                        y: size.height * (0.84 + 0.05 * cos(time * 0.23))
                    )

                    if let artwork {
                        Image(uiImage: artwork)
                            .resizable()
                            .scaledToFill()
                            .blur(radius: 90)
                            .opacity(0.34)
                            .ignoresSafeArea()
                    }

                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [Color.white.opacity(0.02), Color.black.opacity(0.76)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
                .ignoresSafeArea()
            }
        }
    }

    private func orb(color: Color, size: CGFloat, x: CGFloat, y: CGFloat) -> some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .position(x: x, y: y)
            .blur(radius: size * 0.22)
            .opacity(0.52)
            .blendMode(.screen)
    }
}
