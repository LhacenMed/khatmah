#!/usr/bin/env python3
"""Extract mushaf running-head glyphs (sura names + juz numbers) from a QCF font.

The font stores one glyph per mushaf page header, each mapped to a Private-Use
codepoint via the cmap. Glyphs are laid out in two parallel blocks, hafs then
warsh, offset by a fixed gap:

    hafs  suras: glyph120..217 (98)   juz: glyph220..249 (30)
    warsh suras: glyph368..465 (98)   juz: glyph468..497 (30)   (= hafs + 248)

For each glyph we resolve its Unicode codepoint and pair it, in order, with the
sura/juz label it represents, then write one riwaya-specific `headers.json` into
each mushaf repo. That file is the contract the Android app downloads and stores:

    { "riwaya", "font", "version",
      "pages": [ {"page", "codes":[..], "name"}, ... ],  # running-head change points
      "juz":   [ {"num", "code", "name"}, ... 30 ] }

`code`/`codes` are decimal Unicode codepoints (matching the page-JSON `code`).
`pages` is a run-length map: each entry sets the running-head glyph(s) from `page`
onward until the next entry, so the reader resolves any of the 604 pages with a
simple floor lookup. `codes` is usually one glyph but may be several — in
left-to-right draw order (reversed from reading order, since the reader draws the
raw glyph run without bidi) — for a shared page the font has no single glyph for.
The head is computed
here from the repo's own page
data (the actual `surah_header` positions) — the head advances to the glyph of
the last sura that opens on a page and carries forward on continuation pages —
which is why this needs each repo dir, not just the font.

Usage:
    python extract-sura-juz-glyphs.py <font.ttf> \
        --hafs <hafs_repo_dir> --warsh <warsh_repo_dir>
"""

import argparse
import json
import sys
from pathlib import Path

from fontTools.ttLib import TTFont

# ── Glyph layout ──────────────────────────────────────────────────────────
RIWAYA_OFFSET = 248                     # warsh block = hafs block + 248
HAFS_SURA_START, HAFS_SURA_END = 120, 217   # 98 glyphs, inclusive
HAFS_JUZ_START, HAFS_JUZ_END = 220, 249     # 30 glyphs, inclusive

# ── Labels (in glyph order) ─────────────────────────────────────────────────
# 98 page headers from al-Baqarah to an-Nas. Some headers cover several short
# suras that share one mushaf page.
SURA_LABELS = [
    "سورة البقرة", "سورة آل عمران", "سورة النساء", "سورة المائدة",
    "سورة الأنعام", "سورة الأعراف", "سورة الأنفال", "سورة التوبة",
    "سورة يونس", "سورة هود", "سورة يوسف", "سورة الرعد", "سورة إبراهيم",
    "سورة الحجر", "سورة النحل", "سورة الإسراء", "سورة الكهف", "سورة مريم",
    "سورة طه", "سورة الأنبياء", "سورة الحج", "سورة المؤمنون", "سورة النور",
    "سورة الفرقان", "سورة الشعراء", "سورة النمل", "سورة القصص",
    "سورة العنكبوت", "سورة الروم", "سورة لقمان", "سورة السجدة",
    "سورة الأحزاب", "سورة سبأ", "سورة فاطر", "سورة يس", "سورة الصافات",
    "سورة ص", "سورة الزمر", "سورة غافر", "سورة فصلت", "سورة الشورى",
    "سورة الزخرف", "سورة الدخان", "سورة الجاثية", "سورة الأحقاف",
    "سورة محمد", "سورة الفتح", "سورة الحجرات", "سورة ق", "سورة الذاريات",
    "سورة الطور", "سورة النجم", "سورة القمر", "سورة الرحمن", "سورة الواقعة",
    "سورة الحديد", "سورة المجادلة", "سورة الحشر", "سورة الممتحنة",
    "سورة الصف", "سورة الجمعة", "سورة المنافقون", "سورة التغابن",
    "سورة الطلاق", "سورة التحريم", "سورة الملك", "سورة القلم",
    "سورة الحاقة", "سورة المعارج", "سورة نوح", "سورة الجن", "سورة المزمل",
    "سورة المدثر", "سورة القيامة", "سورة الإنسان", "سورة المرسلات",
    "سورة النبأ", "سورة النازعات", "سورة عبس", "سورة التكوير",
    "سورة الانفطار", "سورة المطففين", "سورة الانشقاق", "سورة البروج",
    "سورة الطارق سورة الأعلى", "سورة الغاشية", "سورة الفجر", "سورة البلد",
    "سورة الشمس سورة الليل", "سورة الضحى سورة الشرح", "سورة التين سورة العلق",
    "سورة القدر سورة البينة", "سورة الزلزلة سورة العاديات",
    "سورة القارعة سورة التكاثر", "سورة العصر سورة الهمزة سورة الفيل",
    "سورة قريش سورة الماعون سورة الكوثر", "سورة الكافرون سورة النصر سورة المسد",
    "سورة الإخلاص سورة الفلق سورة الناس",
]

# 30 juz ordinals; label = "الجزء " + ordinal.
JUZ_ORDINALS = [
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع",
    "الثامن", "التاسع", "العاشر", "الحادي عشر", "الثاني عشر", "الثالث عشر",
    "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر", "الثامن عشر",
    "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون",
    "الثالث والعشرون", "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون",
    "السابع والعشرون", "الثامن والعشرون", "التاسع والعشرون", "الثلاثون",
]


FIRST_SURA = 2    # running-head set starts at al-Baqarah (al-Fatiha is excluded)
PAGE_COUNT = 604  # standard madinah layout, both riwayat


def reverse_cmap(font: TTFont) -> dict[str, int]:
    """Map glyph name -> its (lowest) Unicode codepoint from the best cmap."""
    rev: dict[str, int] = {}
    for cp, name in font.getBestCmap().items():
        rev.setdefault(name, cp)
    return rev


def code_at(gid: int, order: list[str], rev: dict[str, int]) -> int:
    """Codepoint of glyph [gid], or raise — a missing code means a broken range."""
    cp = rev.get(order[gid])
    if cp is None:
        raise SystemExit(f"glyph{gid} ({order[gid]}) has no Unicode codepoint")
    return cp


def sura_groups(start: int, order: list[str], rev: dict[str, int]) -> list[dict]:
    """The 98 running-head glyphs; each lists the surah numbers it covers.

    A label's surah count = how many times "سورة" appears in it, so consecutive
    short suras sharing one page (e.g. al-Tariq + al-A'la) walk forward together.
    """
    out, sura = [], FIRST_SURA
    for i, label in enumerate(SURA_LABELS):
        span = label.count("سورة")
        out.append({
            "code": code_at(start + i, order, rev),
            "suras": list(range(sura, sura + span)),
            "name": label,
        })
        sura += span
    assert sura - 1 == 114, f"sura walk ended at {sura - 1}, expected 114"
    return out


def page_runlength(repo: Path, groups: list[dict]) -> list[dict]:
    """Running-head change points across the 604 pages of [repo].

    For each page we read the suras that actually open on it (the `surah_header`
    words). The head shows the glyphs of the distinct groups opening there, in
    order — usually one, but when two suras share a page and the font has no
    single glyph for the pair (only al-Infitar + al-Mutaffifin, page 587) we emit
    both `codes` so the reader draws them combined. Continuation pages carry the
    last opening group (the sura still being read). We emit only the pages where
    the head changes; the app fills forward. Validated to never name an absent sura.
    """
    by_sura = {s: g for g in groups for s in g["suras"]}
    out, last_key, carry = [], None, None
    for page in range(1, PAGE_COUNT + 1):
        data = json.loads((repo / "pages" / f"{page:03d}.json").read_text("utf-8"))
        opens = [w["sura"] for line in data["lines"] for w in line["words"]
                 if w.get("type") == "surah_header"]
        # Distinct groups opening on this page, in surah order (RTL-correct draw order).
        opening, seen = [], set()
        for s in opens:
            g = by_sura.get(s)
            if g and g["code"] not in seen:
                seen.add(g["code"]); opening.append(g)
        if opening:
            display, carry = opening, opening[-1]
        elif carry is not None:
            display = [carry]
        else:
            continue  # before the first running head (al-Fatiha)
        key = tuple(g["code"] for g in display)
        if key != last_key:
            out.append({
                # codes are in left-to-right draw order: the reader draws the raw glyph run
                # without bidi, so a multi-glyph head must be reversed from reading order.
                "page": page,
                "codes": list(reversed(key)),
                "name": " ".join(g["name"] for g in display),
            })
            last_key = key
    return out


def juz_block(start: int, order: list[str], rev: dict[str, int]) -> list[dict]:
    """30 juz entries, each carrying its number and 'الجزء <ordinal>' name."""
    return [{
        "code": code_at(start + i, order, rev),
        "num": i + 1,
        "name": f"الجزء {ordinal}",
    } for i, ordinal in enumerate(JUZ_ORDINALS)]


def main() -> int:
    ap = argparse.ArgumentParser(description="Emit per-riwaya headers.json.")
    ap.add_argument("font", type=Path, help="path to the QCF4_QBSML .ttf font")
    ap.add_argument("--hafs", type=Path, help="khatmah-hafs-qcf4 repo dir")
    ap.add_argument("--warsh", type=Path, help="khatmah-warsh-qcf4 repo dir")
    args = ap.parse_args()

    font = TTFont(args.font)
    order = font.getGlyphOrder()
    rev = reverse_cmap(font)

    assert len(SURA_LABELS) == HAFS_SURA_END - HAFS_SURA_START + 1
    assert len(JUZ_ORDINALS) == HAFS_JUZ_END - HAFS_JUZ_START + 1
    assert HAFS_SURA_END + RIWAYA_OFFSET < len(order), "warsh block exceeds font"

    # riwaya -> (glyph-block offset, repo dir)
    targets = {"hafs": (0, args.hafs), "warsh": (RIWAYA_OFFSET, args.warsh)}
    for riwaya, (offset, repo) in targets.items():
        if repo is None:
            print(f"skip {riwaya}: no repo dir given", file=sys.stderr)
            continue
        groups = sura_groups(HAFS_SURA_START + offset, order, rev)
        data = {
            "riwaya": riwaya,
            "font": "QCF4_QBSML",
            "version": 1,
            "pages": page_runlength(repo, groups),
            "juz": juz_block(HAFS_JUZ_START + offset, order, rev),
        }
        payload = json.dumps(data, ensure_ascii=False, indent=2)
        for dest in (repo / "headers.json", Path(__file__).with_name(f"headers-{riwaya}.json")):
            dest.write_text(payload, encoding="utf-8")
            print(f"Wrote {dest} ({len(data['pages'])} head changes + "
                  f"{len(data['juz'])} juz).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
