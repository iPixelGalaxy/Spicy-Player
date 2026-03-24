import Foundation

struct LyricLetter: Identifiable, Hashable {
    let id = UUID()
    let char: String
    let startMs: Int
    let endMs: Int
}

struct LyricWord: Identifiable, Hashable {
    let id = UUID()
    let text: String
    let startMs: Int
    let endMs: Int
    let isPartOfWord: Bool
    let isLetterGroup: Bool
    let letters: [LyricLetter]

    var duration: Int {
        max(endMs - startMs, 1)
    }
}

struct LyricLine: Identifiable, Hashable {
    let id = UUID()
    let words: [LyricWord]
    let startMs: Int
    let agent: String?
    let isBackground: Bool
    let oppositeAligned: Bool
    let isSongwriter: Bool
    let isInterlude: Bool
    let interludeEndMs: Int

    var endMs: Int {
        if isInterlude, interludeEndMs > 0 {
            return interludeEndMs
        }
        return words.last?.endMs ?? startMs
    }

    var duration: Int {
        max(endMs - startMs, 1)
    }

    var displayText: String {
        if isInterlude {
            return "• • •"
        }

        if words.isEmpty {
            return isSongwriter ? "Written by" : ""
        }

        var result = ""
        for word in words {
            if !result.isEmpty && !word.isPartOfWord {
                result += " "
            }
            result += word.text
        }
        return result
    }
}

struct ParsedLyrics {
    let lines: [LyricLine]
    let songwriters: [String]
}

struct ImportedTrack: Identifiable, Hashable {
    let baseName: String
    let title: String
    let audioURL: URL
    let lyricsURL: URL?

    var id: String {
        baseName.lowercased()
    }

    var hasLyrics: Bool {
        lyricsURL != nil
    }
}
