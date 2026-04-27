#!/usr/bin/env node
/**
 * warsh_updater.js
 *
 * Reconciles a Warsh Quran source file with the `quran` table in quran.db.
 * The DB may have a different ayah count per surah (Hafs vs Warsh numbering).
 *
 * Reconciliation strategy per surah:
 *   • DB ayah exists in source   → UPDATE aya with Warsh text
 *   • Source ayah missing in DB  → INSERT new row (sura_num, sura, aya_num, aya; rest NULL)
 *   • DB ayah missing in source  → UPDATE aya = NULL (or '' if column is NOT NULL)
 *
 * Source format (one surah per line):
 *   sura_num|ayah_text (1) ayah_text (2) ... ayah_text (n)
 *
 * Usage:
 *   node warsh_updater.js --db quran.db --src quran.txt --dry-run
 *
 * Install dependency once:
 *   npm install better-sqlite3
 */

"use strict";

const fs = require("fs");
const path = require("path");

// ── CLI ───────────────────────────────────────────────────────────────────────

function parseArgs() {
  const args = process.argv.slice(2);
  const result = { db: null, src: null, dryRun: false };

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "--db":
        result.db = args[++i];
        break;
      case "--src":
        result.src = args[++i];
        break;
      case "--dry-run":
        result.dryRun = true;
        break;
      default:
        console.error(`Unknown argument: ${args[i]}`);
        process.exit(1);
    }
  }

  if (!result.db || !result.src) {
    console.error(
      "Usage: node warsh_updater.js --db <quran.db> --src <quran.txt> [--dry-run]",
    );
    process.exit(1);
  }

  return result;
}

// ── Unicode-safe trim ─────────────────────────────────────────────────────────

/**
 * Strips only ASCII whitespace (space, tab, CR, LF) from both ends.
 * Deliberately avoids JS's .trim() to preserve U+200F and other Unicode
 * directional/formatting characters embedded in the Arabic text.
 */
function trimAsciiWS(str) {
  return str.replace(/^[ \t\r\n]+|[ \t\r\n]+$/g, "");
}

// ── Parsing ───────────────────────────────────────────────────────────────────

/**
 * Splits a full surah string into Map< aya_num → aya_text >.
 *
 * The marker (n) appears at the END of each ayah, so:
 *   "text_A (1) text_B (2)" → { 1 → "text_A", 2 → "text_B" }
 *
 * [^]*? matches any character including newlines — handles accidental soft-wraps.
 * All Arabic Unicode characters (U+200F RTL mark, Warsh symbols, diacritics) are
 * preserved verbatim; only ASCII whitespace is trimmed around the markers.
 */
function parseSurah(suraNum, rawText) {
  const AYAH_RE = /([^]*?)\s*\((\d+)\)/g;
  const ayahs = new Map();
  let match;

  while ((match = AYAH_RE.exec(rawText)) !== null) {
    const text = trimAsciiWS(match[1]);
    const ayaNum = parseInt(match[2], 10);

    if (!text)
      throw new Error(`Surah ${suraNum}: empty text for ayah ${ayaNum}`);
    if (ayahs.has(ayaNum))
      throw new Error(`Surah ${suraNum}: duplicate marker (${ayaNum})`);

    ayahs.set(ayaNum, text);
  }

  if (ayahs.size === 0)
    throw new Error(`Surah ${suraNum}: no ayah markers found`);

  return ayahs;
}

// ── Source loader ─────────────────────────────────────────────────────────────

/**
 * Reads quran.txt → Map< sura_num, Map< aya_num, text > >
 * Line format:  sura_num|<full surah text with (n) markers>
 * Blank lines and '#' comments are skipped.
 */
function loadSource(srcPath) {
  const lines = fs.readFileSync(srcPath, "utf8").split("\n");
  const quran = new Map();

  for (let i = 0; i < lines.length; i++) {
    const line = trimAsciiWS(lines[i]);
    if (!line || line.startsWith("#")) continue;

    const sep = line.indexOf("|");
    if (sep === -1) {
      console.warn(`[WARN] Line ${i + 1}: missing '|' separator — skipped`);
      continue;
    }

    const suraNum = parseInt(trimAsciiWS(line.slice(0, sep)), 10);
    if (!Number.isInteger(suraNum) || suraNum < 1 || suraNum > 114) {
      console.error(
        `[ERROR] Line ${i + 1}: invalid sura_num '${line.slice(0, sep)}'`,
      );
      process.exit(1);
    }
    if (quran.has(suraNum)) {
      console.error(`[ERROR] Line ${i + 1}: duplicate surah ${suraNum}`);
      process.exit(1);
    }

    // Do NOT trim the surah text — it may start with U+200F or other Unicode markers
    const surahTxt = line.slice(sep + 1);

    try {
      quran.set(suraNum, parseSurah(suraNum, surahTxt));
    } catch (err) {
      console.error(`[ERROR] ${err.message}`);
      process.exit(1);
    }
  }

  return quran;
}

// ── DB helpers ────────────────────────────────────────────────────────────────

/**
 * Checks whether the `aya` column accepts NULL.
 * Returns true if nullable, false if NOT NULL.
 */
function isAyaNullable(db) {
  const cols = db.prepare("PRAGMA table_info('quran')").all();
  const col = cols.find((c) => c.name === "aya");
  // notnull = 1 means NOT NULL constraint; 0 means nullable
  return col ? col.notnull === 0 : true;
}

/**
 * Loads all existing rows for a surah from the DB.
 * Returns { ayaNums: Set<number>, suraName: string }
 */
function loadDbSurah(db, suraNum) {
  const rows = db
    .prepare(
      "SELECT aya_num, sura FROM quran WHERE sura_num = ? ORDER BY aya_num",
    )
    .all(suraNum);

  return {
    ayaNums: new Set(rows.map((r) => r.aya_num)),
    suraName: rows.length > 0 ? rows[0].sura : null,
  };
}

// ── Reconciliation ────────────────────────────────────────────────────────────

/**
 * Reconciles one surah between source and DB.
 * Returns counts { updated, inserted, nulled } for reporting.
 *
 * @param {object}  db        better-sqlite3 instance
 * @param {number}  suraNum   surah number
 * @param {Map}     srcAyahs  Map< aya_num → text > from source
 * @param {object}  stmts     prepared statements { update, insert, nullify }
 * @param {boolean} nullable  whether aya column accepts NULL
 */
function reconcileSurah(db, suraNum, srcAyahs, stmts, nullable) {
  const { ayaNums, suraName } = loadDbSurah(db, suraNum);
  let updated = 0,
    inserted = 0,
    nulled = 0;

  // 1. Walk existing DB ayahs: update those in source, null-out those not in source
  for (const dbAyaNum of ayaNums) {
    if (srcAyahs.has(dbAyaNum)) {
      stmts.update.run(srcAyahs.get(dbAyaNum), suraNum, dbAyaNum);
      updated++;
    } else {
      stmts.nullify.run(nullable ? null : "", suraNum, dbAyaNum);
      nulled++;
    }
  }

  // 2. Insert source ayahs that have no matching row in DB
  for (const [ayaNum, text] of srcAyahs) {
    if (!ayaNums.has(ayaNum)) {
      stmts.insert.run(suraNum, suraName, ayaNum, text);
      inserted++;
    }
  }

  return { updated, inserted, nulled };
}

// ── Plan printer ──────────────────────────────────────────────────────────────

/**
 * Prints a human-readable reconciliation plan for surahs with count mismatches.
 * Runs read-only queries — safe to call before committing anything.
 */
function printPlan(db, quran) {
  let hasMismatch = false;

  console.log(
    "── Reconciliation plan (mismatches only) ────────────────────────",
  );

  for (const [suraNum, srcAyahs] of quran) {
    const { ayaNums } = loadDbSurah(db, suraNum);
    const dbCount = ayaNums.size;
    const srcCount = srcAyahs.size;
    if (dbCount === srcCount) continue;

    hasMismatch = true;
    const toNull = [...ayaNums].filter((n) => !srcAyahs.has(n));
    const toInsert = [...srcAyahs.keys()].filter((n) => !ayaNums.has(n));

    let detail = `  Surah ${String(suraNum).padStart(3)}: DB=${dbCount}, src=${srcCount}`;
    if (toNull.length) detail += `  → null ayahs [${toNull.join(", ")}]`;
    if (toInsert.length) detail += `  → insert ayahs [${toInsert.join(", ")}]`;
    console.log(detail);
  }

  if (!hasMismatch)
    console.log("  All surahs match — no structural changes needed.");
  console.log(
    "─────────────────────────────────────────────────────────────────\n",
  );
}

// ── Table inspector ────────────────────────────────────────────────────
/**
 * TODO: Add inspector for other tables
quran_index
quran_ajzaa
page
android_metadata
rokya
azkar
tasbih
motoun
arba3on_nawawia
arabic_text
quran_pages
times
quran_athman
quran
 */

const Database = require("better-sqlite3");
const db = new Database("quran.db");

// 1. Column definitions (name, type, nullable, default, primary key)
const columns = db.prepare("PRAGMA table_info('quran')").all();
console.log("── Columns ──────────────────────────────");
console.table(columns);

// 2. Indexes
const indexes = db.prepare("PRAGMA index_list('quran')").all();
console.log("── Indexes ──────────────────────────────");
console.table(indexes);

// 3. Foreign keys
const fks = db.prepare("PRAGMA foreign_key_list('quran')").all();
console.log("── Foreign Keys ─────────────────────────");
console.table(fks);

// 4. Raw CREATE TABLE statement (source of truth)
const def = db
  .prepare("SELECT sql FROM sqlite_master WHERE type='table' AND name='quran'")
  .get();
console.log("── CREATE statement ─────────────────────");
console.log(def.sql);

// 5. Row count + quick sample
const count = db.prepare("SELECT COUNT(*) AS cnt FROM quran").get();
console.log(`\n── Row count: ${count.cnt} ───────────────────────`);
// const sample = db.prepare("SELECT * FROM quran LIMIT 3").all();
// console.log("── First 3 rows ─────────────────────────");
// console.table(sample);

db.close();

// ── Main DB update ────────────────────────────────────────────────────────────

function updateDb(dbPath, quran, dryRun) {
  let Database;
  try {
    Database = require("better-sqlite3");
  } catch {
    console.error(
      "[ERROR] Dependency missing. Run:  npm install better-sqlite3",
    );
    process.exit(1);
  }

  const db = new Database(dbPath);
  const nullable = isAyaNullable(db);

  console.log(
    `[INFO] aya column: ${nullable ? "nullable" : "NOT NULL (will use empty string)"}\n`,
  );

  printPlan(db, quran);

  if (dryRun) {
    console.log(
      "[DRY-RUN] Plan shown above. Re-run without --dry-run to apply.",
    );
    db.close();
    return;
  }

  // Prepare statements once — reused for every row (maximum speed)
  const stmts = {
    update: db.prepare(
      "UPDATE quran SET aya = ? WHERE sura_num = ? AND aya_num = ?",
    ),
    nullify: db.prepare(
      "UPDATE quran SET aya = ? WHERE sura_num = ? AND aya_num = ?",
    ),
    insert: db.prepare(
      "INSERT INTO quran (sura_num, sura, aya_num, aya) VALUES (?, ?, ?, ?)",
    ),
  };

  let totalUpdated = 0,
    totalInserted = 0,
    totalNulled = 0;

  const applyAll = db.transaction(() => {
    for (const [suraNum, srcAyahs] of quran) {
      const { updated, inserted, nulled } = reconcileSurah(
        db,
        suraNum,
        srcAyahs,
        stmts,
        nullable,
      );
      totalUpdated += updated;
      totalInserted += inserted;
      totalNulled += nulled;
    }
  });

  try {
    applyAll();
    console.log(
      `[DONE] Updated: ${totalUpdated}  |  Inserted: ${totalInserted}  |  Nulled: ${totalNulled}`,
    );
  } catch (err) {
    console.error(
      `[ERROR] Transaction failed — DB is untouched: ${err.message}`,
    );
    process.exit(1);
  } finally {
    db.close();
  }
}

// ── Entry point ───────────────────────────────────────────────────────────────

function main() {
  const { db, src, dryRun } = parseArgs();

  if (!fs.existsSync(db)) {
    console.error(`[ERROR] DB not found: ${db}`);
    process.exit(1);
  }
  if (!fs.existsSync(src)) {
    console.error(`[ERROR] Source not found: ${src}`);
    process.exit(1);
  }

  console.log(`Loading source: ${path.resolve(src)}`);
  const quran = loadSource(src);
  console.log(`Parsed ${quran.size} surahs from source.\n`);

  updateDb(db, quran, dryRun);
}

main();
