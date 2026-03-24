import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ContentView: View {
    private enum ImportTarget {
        case audio
        case folder
        case lyrics
    }

    @StateObject private var viewModel = PlayerViewModel()
    @State private var isImporterPresented = false
    @State private var activeImportTarget: ImportTarget?

    var body: some View {
        ZStack {
            AnimatedArtworkBackground(artwork: viewModel.artwork)

            VStack(spacing: 16) {
                header

                if !viewModel.libraryTracks.isEmpty {
                    libraryPanel
                }

                lyricsPanel
                controls
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 20)
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
                    case .folder:
                        await viewModel.importFolder(from: url)
                    case .lyrics:
                        await viewModel.importLyricsForCurrentTrack(from: url)
                    case nil:
                        break
                    }
                }
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Spicy Player")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                Text(viewModel.nowPlayingTitle)
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.90))
                    .lineLimit(1)

                Text(viewModel.lyricsStatus)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.68))
                    .lineLimit(2)

                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(Color(red: 1.0, green: 0.72, blue: 0.67))
                        .lineLimit(3)
                }
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing, spacing: 8) {
                actionButton("Import Song", style: .prominent) {
                    activeImportTarget = .audio
                    isImporterPresented = true
                }

                actionButton("Import Folder", style: .prominent) {
                    activeImportTarget = .folder
                    isImporterPresented = true
                }

                actionButton("Attach Lyrics", style: .secondary) {
                    activeImportTarget = .lyrics
                    isImporterPresented = true
                }
                .disabled(!viewModel.canAttachLyrics())
            }
        }
    }

    private var libraryPanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Library")
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.92))

                Spacer()

                Text("\(viewModel.libraryTracks.count) track\(viewModel.libraryTracks.count == 1 ? "" : "s")")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.white.opacity(0.55))
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
        .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var lyricsPanel: some View {
        ScrollViewReader { proxy in
            ScrollView(showsIndicators: false) {
                LazyVStack(spacing: 14) {
                    if viewModel.lines.isEmpty {
                        placeholderView
                    } else {
                        ForEach(viewModel.lines) { line in
                            LyricLineView(
                                line: line,
                                currentTimeMs: viewModel.currentTimeMs
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
                .padding(.vertical, 24)
            }
            .padding(20)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(.white.opacity(0.08), lineWidth: 1)
            )
            .onChange(of: viewModel.currentTimeMs, initial: false) { _, _ in
                guard let activeID = viewModel.activeLineID() else {
                    return
                }

                withAnimation(.spring(response: 0.42, dampingFraction: 0.88)) {
                    proxy.scrollTo(activeID, anchor: .center)
                }
            }
        }
    }

    private var placeholderView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Offline lyric playback")
                .font(.title2.weight(.semibold))
                .foregroundStyle(.white)

            Text("Songs and `.ttml` files are copied into the app's local library on iOS. Import a whole folder to bulk-load tracks, or attach lyrics manually to the current track.")
                .font(.body)
                .foregroundStyle(.white.opacity(0.78))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var controls: some View {
        HStack(spacing: 14) {
            Button(action: viewModel.togglePlayback) {
                Text(viewModel.isPlaying ? "Pause" : "Play")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.96, green: 0.28, blue: 0.20))

            Text(timecode(viewModel.currentTimeMs))
                .font(.system(.headline, design: .monospaced))
                .foregroundStyle(.white.opacity(0.92))
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .background(.white.opacity(0.10), in: Capsule())
        }
    }

    @ViewBuilder
    private func actionButton(_ title: String, style: ActionButtonStyle, action: @escaping () -> Void) -> some View {
        switch style {
        case .prominent:
            Button(title, action: action)
                .buttonStyle(.borderedProminent)
                .tint(Color(red: 0.98, green: 0.36, blue: 0.23))
        case .secondary:
            Button(title, action: action)
                .buttonStyle(.bordered)
                .tint(.white)
        }
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

private enum ActionButtonStyle {
    case prominent
    case secondary
}

private struct LibraryTrackChip: View {
    let track: ImportedTrack
    let isSelected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(track.title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .lineLimit(1)

            Text(track.hasLyrics ? "Lyrics paired" : "No lyrics yet")
                .font(.caption)
                .foregroundStyle(track.hasLyrics ? Color(red: 1.0, green: 0.80, blue: 0.47) : .white.opacity(0.58))
                .lineLimit(1)
        }
        .frame(width: 170, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(isSelected ? Color.white.opacity(0.42) : Color.white.opacity(0.08), lineWidth: 1)
        )
    }

    private var background: LinearGradient {
        if isSelected {
            return LinearGradient(
                colors: [Color(red: 0.98, green: 0.36, blue: 0.23).opacity(0.55), Color.white.opacity(0.12)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }

        return LinearGradient(
            colors: [Color.white.opacity(0.10), Color.white.opacity(0.05)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
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
        composedText()
            .font(baseFont)
            .frame(maxWidth: .infinity, alignment: line.oppositeAligned ? .trailing : .leading)
            .multilineTextAlignment(line.oppositeAligned ? .trailing : .leading)
            .opacity(lineOpacity)
            .scaleEffect(isLineActive ? 1.02 : 1.0, anchor: line.oppositeAligned ? .trailing : .leading)
            .padding(.vertical, line.isInterlude ? 10 : 2)
            .animation(.easeOut(duration: 0.18), value: isLineActive)
    }

    private var baseFont: Font {
        if line.isSongwriter {
            return .system(size: 17, weight: .medium, design: .rounded)
        }

        if line.isInterlude {
            return .system(size: 20, weight: .bold, design: .rounded)
        }

        if line.isBackground {
            return .system(size: 24, weight: isLineActive ? .semibold : .regular, design: .rounded)
        }

        return .system(size: 30, weight: isLineActive ? .bold : .semibold, design: .rounded)
    }

    private var lineOpacity: Double {
        if isLineActive {
            return 1.0
        }
        if isLinePast {
            return 0.72
        }
        return line.isSongwriter ? 0.78 : 0.94
    }

    private func composedText() -> Text {
        if line.isInterlude || line.isSongwriter || line.words.isEmpty {
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
        if line.isInterlude {
            return Color(red: 1.0, green: 0.78, blue: 0.48)
        }
        if line.isSongwriter {
            return .white.opacity(0.86)
        }
        return .white
    }

    private func wordColor(_ word: LyricWord) -> Color {
        if currentTimeMs > word.endMs {
            return .white
        }

        if word.startMs <= currentTimeMs && currentTimeMs <= word.endMs {
            return Color(red: 1.0, green: 0.82, blue: 0.55)
        }

        return upcomingWordColor()
    }

    private func letterColor(_ letter: LyricLetter) -> Color {
        if currentTimeMs > letter.endMs {
            return .white
        }

        if letter.startMs <= currentTimeMs && currentTimeMs <= letter.endMs {
            return Color(red: 1.0, green: 0.82, blue: 0.55)
        }

        return upcomingWordColor()
    }

    private func wordWeight(_ word: LyricWord) -> Font.Weight {
        if word.startMs <= currentTimeMs && currentTimeMs <= word.endMs {
            return .bold
        }
        if currentTimeMs > word.endMs {
            return .semibold
        }
        return .regular
    }

    private func letterWeight(_ letter: LyricLetter) -> Font.Weight {
        if letter.startMs <= currentTimeMs && currentTimeMs <= letter.endMs {
            return .bold
        }
        if currentTimeMs > letter.endMs {
            return .semibold
        }
        return .regular
    }

    private func upcomingWordColor() -> Color {
        if line.isBackground {
            return .white.opacity(0.42)
        }
        return .white.opacity(0.50)
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
                        colors: [Color(red: 0.04, green: 0.05, blue: 0.08), Color(red: 0.14, green: 0.08, blue: 0.10)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )

                    animatedOrb(
                        color: Color(red: 0.96, green: 0.31, blue: 0.21),
                        size: min(size.width, size.height) * 0.72,
                        x: size.width * (0.18 + 0.08 * sin(time * 0.43)),
                        y: size.height * (0.22 + 0.10 * cos(time * 0.31))
                    )

                    animatedOrb(
                        color: Color(red: 1.0, green: 0.77, blue: 0.42),
                        size: min(size.width, size.height) * 0.54,
                        x: size.width * (0.82 + 0.09 * cos(time * 0.28)),
                        y: size.height * (0.34 + 0.10 * sin(time * 0.37))
                    )

                    animatedOrb(
                        color: Color(red: 0.84, green: 0.19, blue: 0.14),
                        size: min(size.width, size.height) * 0.62,
                        x: size.width * (0.56 + 0.07 * sin(time * 0.22)),
                        y: size.height * (0.82 + 0.06 * cos(time * 0.29))
                    )

                    if let artwork {
                        Image(uiImage: artwork)
                            .resizable()
                            .scaledToFill()
                            .blur(radius: 82)
                            .opacity(0.28)
                            .ignoresSafeArea()
                    }

                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [Color.black.opacity(0.08), Color.black.opacity(0.76)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                }
                .ignoresSafeArea()
            }
        }
    }

    private func animatedOrb(color: Color, size: CGFloat, x: CGFloat, y: CGFloat) -> some View {
        Circle()
            .fill(color)
            .frame(width: size, height: size)
            .position(x: x, y: y)
            .blur(radius: size * 0.22)
            .opacity(0.55)
            .blendMode(.screen)
    }
}
