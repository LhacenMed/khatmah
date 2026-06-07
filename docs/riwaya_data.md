# Riwaya Data Bundle — `assets/quran.7z`

## Overview

`quran.7z` is a 7-Zip archive bundled inside the app at `assets/quran.7z`.  
It contains one JSON file per supported riwaya. On first launch, `MushafInitializer` extracts each entry and seeds all relevant tables in `MushafDb` (`mushaf.db`).

This is the **single source of truth** for Quran text, surah metadata, page structure, and division markers for all text-based mushaf modes. It removes the need for `quran.db` entirely.

---

## Archive Contents

| Entry | Riwaya | Verses |
|-------|--------|--------|
| `hafs.json` | Hafs ʿan ʿĀṣim | 6 236 |
| `warsh.json` | Warsh ʿan Nāfiʿ | 6 236 |

---

## JSON Schema

Both files share the same top-level structure.

```jsonc
{
  "riwaya":    "hafs",                         // "hafs" | "warsh" — matches DB riwaya key
  "version":   1,                              // bumped to trigger re-seed on update
  "bismillah": "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ", // riwaya-specific basmala text

  "surahs":      [ ... ],   // 114 entries — see §Surahs
  "verses":      [ ... ],   // 6 236 entries — see §Verses
  "page_starts": [ ... ],   // 604 entries — see §Page Starts
  "divisions":   { ... },   // juzaa / ruba3 / athmaan — see §Divisions
  "sajdaat":     [ ... ]    // 14–15 entries — see §Sajdaat
}
```

### Surahs

One object per surah, ordered 1 → 114.

```jsonc
{
  "num":              1,            // 1-based surah number
  "name":             "الفاتحة",   // Arabic name
  "name_en":          "Al-Fatihah",// Simple English transliteration
  "name_complex":     "Al-Fātiĥah",// Diacritical transliteration
  "translated_name":  "The Opener",// English meaning
  "type":             "makki",     // "makki" | "madani"
  "revelation_order": 5,           // Chronological revelation index (1–114)
  "ayat":             7,           // Verse count — riwaya-specific (e.g. 285 Warsh / 286 Hafs for Al-Baqarah)
  "bismillah_pre":    false        // true = basmala shown before this surah; false = omitted (surah 1 & 9)
}
```

> **`bismillah_pre`** replaces the old hardcoded `suraNum != 1 && suraNum != 9` logic in `QuranPageBuilder`.  
> Always read from this field — do not hardcode surah numbers.

### Verses

One object per verse, ordered by surah then aya number.

```jsonc
{
  "sura": 1,
  "aya":  1,
  "text": "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"  // Uthmani script, riwaya-specific orthography
}
```

- Text uses the **Uthmani script** with full diacritics and special Quranic characters.
- `VerseEntity.normalized` is computed at seed time via `String.normalizeArabic()` (diacritics stripped, alef variants unified) and stored separately for fast in-memory search — it is **not** present in the JSON.
- The Warsh file uses Warsh-specific orthography (e.g. different hamza forms, alef maqsura usage).

### Page Starts

Maps each 1-based page number to the first verse on that page. 604 entries for a standard mushaf.

```jsonc
{ "num": 1,   "sura": 1,  "aya": 1 },
{ "num": 2,   "sura": 2,  "aya": 1 },
{ "num": 3,   "sura": 2,  "aya": 6 },
{ "num": 604, "sura": 112,"aya": 1 }
```

> The Hafs and Warsh masahif have **different page layouts** — do not share `page_starts` between riwayat.

### Divisions

A single nested object with three arrays. Each marker identifies the **first verse** of that division unit.

```jsonc
"divisions": {
  "juzaa":   [ { "num": 1,  "sura": 1, "aya": 1   }, ... ],  // 30 entries
  "ruba3":   [ { "num": 1,  "sura": 1, "aya": 1   }, ... ],  // 240 entries (rub' al-hizb)
  "athmaan": [ { "num": 1,  "sura": 1, "aya": 1   }, ... ]   // 480 entries (Warsh only); empty array [] for Hafs
}
```

| Key | Description | Count |
|-----|-------------|-------|
| `juzaa` | Juz' start markers | 30 |
| `ruba3` | Rub' al-hizb (¼ hizb) start markers | 240 |
| `athmaan` | Thumn al-hizb (⅛ hizb) start markers — Moroccan Warsh masahif only | 480 (Warsh) / 0 (Hafs) |

> **Hizb number** is not stored; derive it: `hizb = ceil(rub_num / 4)`.

### Sajdaat

Prostration (سجدة) verse markers. 15 entries for Hafs (standard Medina mushaf), may vary for other riwayat.

```jsonc
{
  "num":        1,
  "sura":       7,
  "aya":        206,
  "obligatory": false   // true = wajib (Ḥanafī ruling); false = mustaḥabb
}
```

---

## DB Mapping

`MushafInitializer` parses each JSON entry and inserts into these `MushafDb` tables. All rows are keyed by `riwaya` (the string value from the JSON's `"riwaya"` field).

| JSON section | Room entity | Table |
|---|---|---|
| `surahs[]` | `SurahEntity` | `mushaf_surah` |
| `verses[]` | `VerseEntity` | `mushaf_verse` |
| `page_starts[]` | `PageStartEntity` | `mushaf_page_start` |
| `divisions.juzaa[]` | `DivisionEntity` (type=`"juz"`) | `mushaf_division` |
| `divisions.ruba3[]` | `DivisionEntity` (type=`"rub"`) | `mushaf_division` |
| `divisions.athmaan[]` | `DivisionEntity` (type=`"thumn"`) | `mushaf_division` |
| `sajdaat[]` | `SajdaEntity` | `mushaf_sajda` |

QCF4 glyph data (downloaded separately) populates `mushaf_page`, `mushaf_word`, and `mushaf_verse_page` — not sourced from this archive.

---

## Versioning & Re-seed

The `"version"` field controls re-seeding. `MushafInitializer` stores the last seeded version in `SharedPreferences` under the key `<riwaya>_version` (e.g. `hafs_version`).

- If `seeded_version >= json.version` → skip (already current).
- If `seeded_version < json.version` → clear and re-seed all tables for that riwaya.
- If `seeded_version == 0` → first launch; seed unconditionally.

To force a re-seed in a future app update, increment `"version"` in the JSON file.

---

## Relationship to QCF4 Downloads

The bundled JSON provides everything needed for the **text mushaf** (rendered via `QuranPageBuilder` + `QuranPageRenderer` with `kfgqpc_hafs_uthmanic.ttf` / `kfgqpc_warsh_uthmanic.ttf`).

When the user downloads a **QCF4 mushaf** (Hafs or Warsh), the download covers only:
- QCF4 glyph font files (TTF)
- Per-page glyph layout JSON (604 files)

It does **not** download a separate `meta.json` — surah metadata, divisions, sajdaat, and page starts are already in `MushafDb` from this bundle.

```
quran.7z (bundled)                    QCF4 CDN (downloaded on demand)
├── hafs.json  ──► mushaf_surah       ├── fonts/QCF4_Hafs_01_W.ttf … 49 files
│               ──► mushaf_verse      └── pages/001.json … 604 files
│               ──► mushaf_division        │
│               ──► mushaf_sajda           ▼
│               ──► mushaf_page_start  mushaf_page
└── warsh.json  (same structure)      mushaf_word
                                      mushaf_verse_page
```

---

## Font Selection

The riwaya key drives font selection at render time:

| Riwaya | Text font | Surah header font |
|--------|-----------|-------------------|
| `hafs` | `R.font.kfgqpc_hafs_uthmanic` (`HafsFamily`) | `R.font.hafs_sura_name` (`HafsSuraNameFamily`) |
| `warsh` | `R.font.kfgqpc_warsh_uthmanic` (`WarshFamily`) | `R.font.warsh_sura_name` (`WarshSuraNameFamily`) |

Both fonts are bundled in `res/font/` and require no download.

---

## Adding a New Riwaya

1. Add the JSON file to the archive as `<riwaya_key>.json` (e.g. `qalun.json`).
2. Add the enum value to `Riwaya.kt`.
3. Add a `MushafPrint` entry for it in `MushafPrint.kt` and register it in `MushafRegistry`.
4. Bundle the appropriate fonts in `res/font/` and update the font selection logic in `QuranPageRenderer`.
5. `MushafInitializer` will pick up the new entry automatically on the next launch.

---

## Key Files

| File | Role |
|------|------|
| `assets/quran.7z` | This archive |
| `MushafInitializer.kt` | Extracts archive entries and seeds `MushafDb` on first launch |
| `MushafMeta.kt` | JSON parsing models + entity converters + `normalizeArabic()` |
| `MushafEntities.kt` | Room entity definitions for all seeded tables |
| `MushafDao.kt` | DB access — `insertRiwayaData()` is the bulk-insert entry point |
| `MushafDb.kt` | Room database (version 3) — single DB for both riwaya and QCF4 data |
| `QuranRepository.kt` | Reads from `MushafDb`; provides `allAyas()`, `search()`, `ayaPageIndex()` |
| `QuranPageBuilder.kt` | Paginates ayas into `QuranPageData`; consumes `bismillah_pre` from DB |
| `QuranPageRenderer.kt` | Renders pages; selects font family based on `Riwaya` |