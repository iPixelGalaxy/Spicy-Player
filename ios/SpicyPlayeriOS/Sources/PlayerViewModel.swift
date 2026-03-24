import AVFoundation
import SwiftUI
import UIKit
import UniformTypeIdentifiers

@MainActor
final class PlayerViewModel: ObservableObject {
    @Published var lines: [LyricLine] = []
    @Published var currentTimeMs = 0
    @Published var isPlaying = false
    @Published var artwork: UIImage?
    @Published var nowPlayingTitle = "No Track Loaded"
    @Published var lyricsStatus = "Pick an audio file to start."
    @Published var errorMessage: String?

    private let player = AVPlayer()
    private var timeObserver: Any?
    private var activeURLs: [URL] = []

    init() {
        configureAudioSession()
        installTimeObserver()
    }

    func supportedTypes() -> [UTType] {
        var result: [UTType] = [.audio]
        if let flac = UTType(filenameExtension: "flac") {
            result.append(flac)
        }
        return result
    }

    func importSong(from url: URL) async {
        errorMessage = nil
        releaseSecurityScopes()
        artwork = nil
        lines = []

        if url.startAccessingSecurityScopedResource() {
            activeURLs = [url]
        }
        nowPlayingTitle = url.deletingPathExtension().lastPathComponent

        let lyricsURL = url.deletingPathExtension().appendingPathExtension("ttml")
        if FileManager.default.fileExists(atPath: lyricsURL.path), lyricsURL.startAccessingSecurityScopedResource() {
            activeURLs.append(lyricsURL)
        }

        await loadLyrics(from: lyricsURL)
        await loadArtwork(from: url)
        replaceCurrentItem(with: url)
    }

    func togglePlayback() {
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            player.play()
            isPlaying = true
        }
    }

    func seek(to timeMs: Int) {
        let time = CMTime(seconds: Double(timeMs) / 1000.0, preferredTimescale: 600)
        player.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero)
        currentTimeMs = timeMs
    }

    func activeLineID() -> UUID? {
        if let current = lines.first(where: { !$0.isBackground && !$0.isSongwriter && $0.startMs <= currentTimeMs && currentTimeMs <= $0.endMs }) {
            return current.id
        }

        return lines
            .filter { !$0.isBackground && !$0.isSongwriter && $0.startMs <= currentTimeMs }
            .last?
            .id
    }

    private func configureAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func installTimeObserver() {
        let interval = CMTime(seconds: 1.0 / 30.0, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self else {
                return
            }

            let milliseconds = Int((time.seconds * 1000.0).rounded())
            if milliseconds >= 0 {
                self.currentTimeMs = milliseconds
            }

            if let currentItem = self.player.currentItem {
                let isAtEnd = currentItem.status == .readyToPlay && currentItem.duration.isNumeric && time >= currentItem.duration
                if isAtEnd {
                    self.isPlaying = false
                }
            }
        }
    }

    private func replaceCurrentItem(with url: URL) {
        let item = AVPlayerItem(url: url)
        player.replaceCurrentItem(with: item)
        player.play()
        isPlaying = true
        currentTimeMs = 0
    }

    private func loadLyrics(from url: URL) async {
        do {
            let data = try Data(contentsOf: url)
            let parsed = try TTMLLyricsParser.parse(data: data)
            lines = parsed.lines
            lyricsStatus = parsed.lines.isEmpty ? "No lyric lines found in \(url.lastPathComponent)." : url.lastPathComponent
        } catch {
            lines = []
            lyricsStatus = "No paired TTML file found."
            if FileManager.default.fileExists(atPath: url.path) {
                errorMessage = "Lyrics could not be parsed: \(error.localizedDescription)"
            }
        }
    }

    private func loadArtwork(from url: URL) async {
        let asset = AVURLAsset(url: url)

        do {
            let metadata = try await asset.load(.commonMetadata)
            if let item = metadata.first(where: { $0.commonKey?.rawValue == "artwork" }),
               let data = try await item.load(.dataValue),
               let image = UIImage(data: data) {
                artwork = image
            } else {
                artwork = nil
            }
        } catch {
            artwork = nil
        }
    }

    private func releaseSecurityScopes() {
        for url in activeURLs {
            url.stopAccessingSecurityScopedResource()
        }
        activeURLs.removeAll()
    }
}

private extension CMTime {
    var isNumeric: Bool {
        flags.contains(.valid) && !seconds.isNaN && !seconds.isInfinite
    }
}
