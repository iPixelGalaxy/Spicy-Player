import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = PlayerViewModel()
    @State private var isImporterPresented = false

    var body: some View {
        ZStack {
            backgroundView

            VStack(spacing: 20) {
                header
                lyricsPanel
                controls
            }
            .padding(.horizontal, 20)
            .padding(.top, 28)
            .padding(.bottom, 20)
        }
        .fileImporter(
            isPresented: $isImporterPresented,
            allowedContentTypes: viewModel.supportedTypes(),
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else {
                    return
                }
                Task {
                    await viewModel.importSong(from: url)
                }
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private var backgroundView: some View {
        ZStack {
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.07, blue: 0.10), Color(red: 0.11, green: 0.08, blue: 0.14)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            if let artwork = viewModel.artwork {
                Image(uiImage: artwork)
                    .resizable()
                    .scaledToFill()
                    .blur(radius: 70)
                    .opacity(0.45)
                    .overlay(Color.black.opacity(0.45))
                    .ignoresSafeArea()
            }

            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [Color.black.opacity(0.10), Color.black.opacity(0.72)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
        }
        .ignoresSafeArea()
    }

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Spicy Player")
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                Text(viewModel.nowPlayingTitle)
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.88))
                    .lineLimit(1)

                Text(viewModel.lyricsStatus)
                    .font(.subheadline)
                    .foregroundStyle(.white.opacity(0.62))
                    .lineLimit(2)
            }

            Spacer()

            Button("Load Song") {
                isImporterPresented = true
            }
            .buttonStyle(.borderedProminent)
            .tint(Color(red: 0.98, green: 0.36, blue: 0.23))
        }
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

                withAnimation(.spring(response: 0.45, dampingFraction: 0.88)) {
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

            Text("Choose a local audio file. If a matching `.ttml` file sits beside it with the same base name, the app will load synchronized lyrics automatically.")
                .font(.body)
                .foregroundStyle(.white.opacity(0.78))

            if let errorMessage = viewModel.errorMessage {
                Text(errorMessage)
                    .font(.footnote)
                    .foregroundStyle(Color(red: 1.0, green: 0.72, blue: 0.67))
            }
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

    private func timecode(_ ms: Int) -> String {
        let totalSeconds = max(ms / 1000, 0)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

private struct LyricLineView: View {
    let line: LyricLine
    let currentTimeMs: Int

    var body: some View {
        let active = line.startMs <= currentTimeMs && currentTimeMs <= line.endMs
        let past = currentTimeMs > line.endMs

        Text(line.displayText.isEmpty ? " " : line.displayText)
            .font(font(active: active))
            .frame(maxWidth: .infinity, alignment: line.oppositeAligned ? .trailing : .leading)
            .foregroundStyle(foreground(active: active, past: past))
            .opacity(opacity(active: active, past: past))
            .scaleEffect(active ? 1.02 : 1.0, anchor: line.oppositeAligned ? .trailing : .leading)
            .padding(.vertical, line.isInterlude ? 10 : 2)
            .animation(.easeOut(duration: 0.18), value: active)
    }

    private func font(active: Bool) -> Font {
        if line.isSongwriter {
            return .system(size: 17, weight: .medium, design: .rounded)
        }

        if line.isInterlude {
            return .system(size: 20, weight: .bold, design: .rounded)
        }

        if line.isBackground {
            return .system(size: 24, weight: active ? .semibold : .regular, design: .rounded)
        }

        return .system(size: 30, weight: active ? .bold : .semibold, design: .rounded)
    }

    private func foreground(active: Bool, past: Bool) -> Color {
        if line.isInterlude {
            return Color(red: 1.0, green: 0.78, blue: 0.48)
        }
        if active {
            return .white
        }
        if past {
            return .white.opacity(0.86)
        }
        return .white.opacity(line.isBackground ? 0.48 : 0.62)
    }

    private func opacity(active: Bool, past: Bool) -> Double {
        if active {
            return 1.0
        }
        if past {
            return 0.72
        }
        return line.isSongwriter ? 0.78 : 0.92
    }
}
