package com.lhacenmed.khatmah.data.audio

// ── Reader manifest (from assets/readers.json) ────────────────────────────────

data class ReaderManifest(val readers: List<ReaderInfo>)

data class ReaderInfo(
    val id:       String,
    val name:     String,
    val folderId: String,
)

// ── Folder index (cached per reader in filesDir) ──────────────────────────────

/**
 * Cached Drive listing for one reader: maps surah number → file IDs.
 * Persisted as JSON at filesDir/audio/{readerId}/index.json.
 */
data class ReaderIndex(
    /** surahNum (as string key) → SurahFiles */
    val surahs: Map<String, SurahFiles>,
)

data class SurahFiles(val mp3Id: String, val jsonId: String)

// ── Transcript JSON (from Drive) ──────────────────────────────────────────────

data class TranscriptSegment(
    val start: Double,
    val end:   Double,
    val text:  String,
    val words: List<TranscriptWord>,
)

data class TranscriptWord(
    val word:  String,
    val start: Double,
    val end:   Double,
    val score: Double,
)

// ── Playback source resolved from transcript ──────────────────────────────────

/**
 * Resolved seek position and aya timeline for a surah session.
 *
 * [seekMs]     — where to seek before starting playback for the selected aya.
 * [timeline]   — ordered list of (ayaNum, startMs) for auto-advance.
 * [basmalaSeg] — true when segment[0] is a standalone basmala (not aya 1).
 */
data class PlaybackSource(
    val seekMs:     Int,
    val timeline:   List<Pair<Int, Int>>,
    val basmalaSeg: Boolean,
)