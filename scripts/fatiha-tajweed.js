/**
 * fatiha-tajweed.js
 * Fetches Surah Al-Fatiha Uthmani Tajweed from the Quran Foundation API,
 * then generates fatiha.html — open it in any browser to see color-coded tajweed.
 *
 * Setup: create a .env file in the same directory:
 *   QURAN_CLIENT_ID=your_client_id
 *   QURAN_CLIENT_SECRET=your_client_secret
 *   QURAN_ENV=production          # or: prelive  (optional, defaults to production)
 *
 * Usage:
 *   node fatiha-tajweed.js
 */

const https = require("https");
const fs = require("fs");
const path = require("path");

// ─── .env Loader (no external dependencies) ───────────────────────────────────

(function loadEnv() {
  const envPath = path.join(__dirname, ".env");
  if (!fs.existsSync(envPath)) return;
  fs.readFileSync(envPath, "utf8")
    .split("\n")
    .forEach((line) => {
      const clean = line.trim();
      if (!clean || clean.startsWith("#")) return;
      const eq = clean.indexOf("=");
      if (eq < 0) return;
      const key = clean.slice(0, eq).trim();
      const val = clean
        .slice(eq + 1)
        .trim()
        .replace(/^["']|["']$/g, "");
      if (key && !(key in process.env)) process.env[key] = val;
    });
})();

// ─── Config ──────────────────────────────────────────────────────────────────

const IS_PRELIVE = (process.env.QURAN_ENV ?? "production") === "prelive";

const BASE_URL = IS_PRELIVE
  ? "https://apis-prelive.quran.foundation/content/api/v4"
  : "https://apis.quran.foundation/content/api/v4";

const TOKEN_URL = IS_PRELIVE
  ? "https://prelive-oauth2.quran.foundation/oauth2/token"
  : "https://oauth2.quran.foundation/oauth2/token";

const CLIENT_ID = process.env.QURAN_CLIENT_ID;
const CLIENT_SEC = process.env.QURAN_CLIENT_SECRET;

if (!CLIENT_ID || !CLIENT_SEC) {
  console.error(
    "✗ Missing credentials. Add QURAN_CLIENT_ID and QURAN_CLIENT_SECRET to your .env file.",
  );
  process.exit(1);
}

// ─── Token Cache ──────────────────────────────────────────────────────────────

const token = { value: null, expiresAt: 0 };

const tokenValid = () => token.value && Date.now() < token.expiresAt - 30_000;

/** Fetches a fresh access_token via client_credentials grant (HTTP Basic auth). */
async function fetchToken() {
  // Quran Foundation requires client_secret_basic:
  // credentials go in the Authorization header, NOT in the request body.
  const basic = Buffer.from(`${CLIENT_ID}:${CLIENT_SEC}`).toString("base64");
  const body = "grant_type=client_credentials&scope=content";
  const data = await request(TOKEN_URL, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
      "Content-Length": Buffer.byteLength(body),
    },
    body,
  });
  token.value = data.access_token;
  token.expiresAt = Date.now() + (data.expires_in ?? 3600) * 1000;
  return token.value;
}

const getToken = () =>
  tokenValid() ? Promise.resolve(token.value) : fetchToken();

// ─── HTTP Helper ──────────────────────────────────────────────────────────────

function request(url, opts = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    if (opts.params) {
      Object.entries(opts.params).forEach(([k, v]) => {
        if (v != null) parsed.searchParams.set(k, v);
      });
    }
    const req = https.request(
      {
        hostname: parsed.hostname,
        path: parsed.pathname + parsed.search,
        method: opts.method ?? "GET",
        headers: opts.headers ?? {},
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString();
          if (res.statusCode >= 400) {
            const err = new Error(`HTTP ${res.statusCode}`);
            err.status = res.statusCode;
            err.body = raw;
            return reject(err);
          }
          try {
            resolve(JSON.parse(raw));
          } catch {
            resolve(raw);
          }
        });
      },
    );
    req.on("error", reject);
    if (opts.body) req.write(opts.body);
    req.end();
  });
}

// ─── API Client ───────────────────────────────────────────────────────────────

const MAX_RETRIES = 3;
const BACKOFF_BASE = 1000; // ms
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Calls a Content API endpoint with the required auth headers.
 * Handles 401 (re-auth + retry once), 403, and 429 (exponential backoff).
 */
async function apiGet(endpoint, params = {}, attempt = 0) {
  const accessToken = await getToken();
  try {
    return await request(`${BASE_URL}${endpoint}`, {
      headers: { "x-auth-token": accessToken, "x-client-id": CLIENT_ID },
      params,
    });
  } catch (err) {
    if (err.status === 401 && attempt === 0) {
      token.value = null; // force refresh
      return apiGet(endpoint, params, 1);
    }
    if (err.status === 403) {
      throw new Error("Access denied (403). Verify CLIENT_ID and scopes.");
    }
    if (err.status === 429 && attempt < MAX_RETRIES) {
      const delay = BACKOFF_BASE * 2 ** attempt;
      console.warn(
        `⚠ Rate limited. Retrying in ${delay}ms… (${attempt + 1}/${MAX_RETRIES})`,
      );
      await sleep(delay);
      return apiGet(endpoint, params, attempt + 1);
    }
    throw err;
  }
}

// ─── Tafkhim Post-Processor ───────────────────────────────────────────────────

/**
 * The Quran Foundation API does not annotate Tafkhim (تفخيم) letters.
 * This function detects the 7 strong emphatic letters (حروف الاستعلاء):
 *   خ ص ض ط ظ غ ق
 * along with any immediately following diacritics, and wraps them in a
 * <tajweed class=tafkhim> tag — but ONLY in plain-text segments, never
 * inside an already-annotated <tajweed ...> span.
 *
 * @param {string} html  Raw verse HTML from the API
 * @returns {string}     HTML with tafkhim letters annotated
 */
function addTafkhim(html) {
  // Matches one tafkhim base letter + any trailing Arabic diacritics
  const TAFKHIM_RE = /([خصضطظغق][\u0610-\u061A\u064B-\u065F]*)/gu;
  // Matches an existing <tajweed ...>…</tajweed> span (greedy-safe: non-nested)
  const EXISTING_TAG_RE = /(<tajweed[^>]*>.*?<\/tajweed>)/gs;

  const parts = [];
  let cursor = 0;
  let m;

  EXISTING_TAG_RE.lastIndex = 0;
  while ((m = EXISTING_TAG_RE.exec(html)) !== null) {
    // Plain-text segment before this existing tag → annotate tafkhim
    parts.push(
      html
        .slice(cursor, m.index)
        .replace(TAFKHIM_RE, "<tajweed class=tafkhim>$1</tajweed>"),
    );
    // Existing tag → pass through untouched
    parts.push(m[0]);
    cursor = m.index + m[0].length;
  }

  // Remaining plain-text segment after last tag
  parts.push(
    html
      .slice(cursor)
      .replace(TAFKHIM_RE, "<tajweed class=tafkhim>$1</tajweed>"),
  );

  return parts.join("");
}

// ─── HTML Builder ─────────────────────────────────────────────────────────────

/**
 * Builds a self-contained HTML page from the raw API verse array.
 * tajweed tags are embedded exactly as returned by the API — no transformation.
 * Tafkhim letters are annotated via addTafkhim() before rendering.
 * CSS handles all color-coding via standard class selectors.
 *
 * Color values are sourced from the authoritative alquran.cloud tajweed guide:
 * https://alquran.cloud/tajweed-guide
 * Tafkhim color matches the standard Mushaf Tajweed (Dar al-Maarifah) brown.
 *
 * @param {Array<{verse_key: string, text_uthmani_tajweed: string}>} verses
 * @returns {string} Complete HTML document
 */
function buildHtml(verses) {
  const ayat = verses
    .map(
      ({ verse_key, text_uthmani_tajweed }) => `
      <div class="ayah">
        <span class="ayah-num">${verse_key.split(":")[1]}</span>
        <span class="ayah-text">${addTafkhim(text_uthmani_tajweed)}</span>
      </div>`,
    )
    .join("\n");

  return `<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>سُورَةُ الْفَاتِحَة — تجويد</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Amiri+Quran&family=Amiri:wght@400;700&display=swap" rel="stylesheet">
  <style>
    /* ── Reset & Base ───────────────────────────────────────── */
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

    :root {
      --bg:       #0f0e0c;
      --surface:  #1a1814;
      --border:   #2e2a22;
      --gold:     #c9a84c;
      --gold-dim: #7a6330;
      --text:     #e8dfc8;
      --text-dim: #8a7f6a;
      --radius:   12px;

      /*
       * Tajweed color palette — exact hex values from alquran.cloud/tajweed-guide
       * Source: https://alquran.cloud/tajweed-guide
       * Tafkhim color matches Dar al-Maarifah Mushaf Tajweed (brown).
       */
      --c-silent:     #AAAAAA;  /* ham_wasl · slnt · laam_shamsiyah             */
      --c-madd-2:     #537FFF;  /* madda_normal       — مدّ حركتان             */
      --c-madd-246:   #4050FF;  /* madda_permissible  — مدّ 2/4/6 جوازاً       */
      --c-madd-6:     #000EBC;  /* madda_necessary    — مدّ 6 لزوماً           */
      --c-madd-sat:   #2144C1;  /* madda_obligatory   — مدّ مشبع 6 حركات       */
      --c-qalaqah:    #DD0008;  /* qalaqah            — قلقلة                   */
      --c-ikhfa-sh:   #D500B7;  /* ikhfa_shafawi      — إخفاء شفوي             */
      --c-ikhfa:      #9400A8;  /* ikhfa              — إخفاء                   */
      --c-idgh-sh:    #58B800;  /* idghaam_shafawi    — إدغام شفوي             */
      --c-iqlab:      #26BFFD;  /* iqlab              — إقلاب                   */
      --c-idgh-ghn:   #169777;  /* idghaam_ghunnah    — إدغام بغنة             */
      --c-idgh-wghn:  #169200;  /* idghaam_wo_ghunnah — إدغام بدون غنة         */
      --c-idgh-mut:   #A1A1A1;  /* idghaam_mutajanisayn/mutaqaribayn            */
      --c-ghunnah:    #FF7E1E;  /* ghunnah            — غنة                     */
      --c-tafkhim:    #A0522D;  /* tafkhim            — تفخيم (حروف الاستعلاء) */
    }

    html, body {
      min-height: 100vh;
      background: var(--bg);
      color: var(--text);
      font-family: 'Amiri', serif;
    }

    body {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 48px 24px 80px;
    }

    /* ── Header ────────────────────────────────────────────── */
    header { text-align: center; margin-bottom: 48px; }

    .surah-title {
      font-family: 'Amiri Quran', serif;
      font-size: clamp(1.8rem, 5vw, 2.8rem);
      color: var(--gold);
      line-height: 1.6;
      display: block;
    }

    .surah-sub {
      font-size: 0.9rem;
      color: var(--text-dim);
      margin-top: 6px;
      font-family: 'Amiri', serif;
    }

    /* ── Surah Card ─────────────────────────────────────────── */
    .surah-card {
      width: 100%;
      max-width: 860px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 40px 48px;
    }

    .divider {
      width: 100%;
      height: 1px;
      background: linear-gradient(to left, transparent, var(--gold-dim), transparent);
      margin-bottom: 32px;
    }

    /* ── Ayah rows ──────────────────────────────────────────── */
    .ayah {
      display: flex;
      align-items: baseline;
      gap: 16px;
      padding: 22px 0;
      border-bottom: 1px solid var(--border);
    }
    .ayah:last-child { border-bottom: none; }

    .ayah-num {
      font-size: 0.75rem;
      color: var(--gold-dim);
      font-family: 'Amiri', serif;
      direction: ltr;
      min-width: 28px;
      text-align: center;
      flex-shrink: 0;
      padding-top: 6px;
    }

    .ayah-text {
      flex: 1;
      font-family: 'Amiri Quran', serif;
      font-size: clamp(1.6rem, 3.5vw, 2.1rem);
      line-height: 2.4;
      color: var(--text);
    }

    /* Verse-end circle from API <span class=end> */
    .ayah-text span.end {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 1.8em;
      height: 1.8em;
      border-radius: 50%;
      border: 1.5px solid var(--gold-dim);
      color: var(--gold);
      font-size: 0.7em;
      margin-inline-start: 4px;
      vertical-align: middle;
      font-family: 'Amiri', serif;
    }

    /* ── Tajweed tag — base inline element ──────────────────── */
    tajweed { display: inline; }

    /* ── Tajweed color classes ──────────────────────────────── */
    /* Silent / Hamzat Wasl / Laam Shamsiyah */
    .ham_wasl,
    .slnt,
    .laam_shamsiyya,
    .laam_shamsiyah       { color: var(--c-silent); }

    /* Madd (prolongation) */
    .madda_normal         { color: var(--c-madd-2);   }
    .madda_permissible    { color: var(--c-madd-246); }
    .madda_necessary      { color: var(--c-madd-6);   }
    .madda_obligatory     { color: var(--c-madd-sat); }

    /* Qalqalah */
    .qalaqah              { color: var(--c-qalaqah);  }

    /* Tafkhim — حروف الاستعلاء (annotated client-side; not in API output) */
    .tafkhim              { color: var(--c-tafkhim);  }

    /* Ikhfa */
    .ikhfa_shafawi        { color: var(--c-ikhfa-sh); }
    .ikhfa                { color: var(--c-ikhfa);    }

    /* Idghaam */
    .idghaam_shafawi      { color: var(--c-idgh-sh);  }
    .idghaam_ghunnah      { color: var(--c-idgh-ghn); }
    .idghaam_wo_ghunnah   { color: var(--c-idgh-wghn);}
    .idghaam_mutajanisayn,
    .idghaam_mutaqaribayn { color: var(--c-idgh-mut); }

    /* Ghunnah & Iqlab */
    .ghunnah              { color: var(--c-ghunnah);  }
    .iqlab                { color: var(--c-iqlab);    }

    /* ── Legend ─────────────────────────────────────────────── */
    .legend {
      width: 100%;
      max-width: 860px;
      margin-top: 36px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: var(--radius);
      padding: 28px 36px 32px;
    }

    .legend-heading {
      font-family: 'Amiri', serif;
      font-size: 1rem;
      font-weight: 700;
      color: var(--gold);
      text-align: center;
      margin-bottom: 24px;
      letter-spacing: 0.04em;
    }

    /* Two column layout mirroring the mushaf legend style */
    .legend-cols {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 6px 48px;
    }

    @media (max-width: 540px) {
      .legend-cols { grid-template-columns: 1fr; }
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 7px 0;
      border-bottom: 1px solid var(--border);
      font-family: 'Amiri', serif;
      font-size: 1rem;
      color: var(--text);
      direction: rtl;
    }
    .legend-item:last-child,
    .legend-item:nth-last-child(2):nth-child(odd) { border-bottom: none; }

    .legend-dot {
      width: 13px;
      height: 13px;
      border-radius: 50%;
      flex-shrink: 0;
      order: 1; /* dot appears on the left in RTL flow */
    }

    .legend-label { order: 2; }
  </style>
</head>
<body>

  <header>
    <span class="surah-title">سُورَةُ الْفَاتِحَة</span>
    <p class="surah-sub">النص العثماني بالتجويد الملوّن</p>
  </header>

  <div class="surah-card">
    <div class="divider"></div>
${ayat}
  </div>

  <!-- Legend — 8 categories matching the standard Mushaf Tajweed colour guide -->
  <div class="legend">
    <p class="legend-heading">دليل ألوان التجويد</p>
    <div class="legend-cols">

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-ikhfa)"></span>
        <span class="legend-label">إخفاء، ومواقع الغُنّة (حركتان)</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-tafkhim)"></span>
        <span class="legend-label">تفخيم</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-qalaqah)"></span>
        <span class="legend-label">قلقلة</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-idgh-ghn)"></span>
        <span class="legend-label">إدغام، وما لا يلفظ</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-madd-6)"></span>
        <span class="legend-label">مد 6 حركات لزوماً</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-madd-246)"></span>
        <span class="legend-label">مد 2 أو 4 أو 6 جوازاً</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-madd-sat)"></span>
        <span class="legend-label">مد مشبع 6 حركات</span>
      </div>

      <div class="legend-item">
        <span class="legend-dot" style="background:var(--c-madd-2)"></span>
        <span class="legend-label">مد حركتان</span>
      </div>

    </div>
  </div>

</body>
</html>`;
}

// ─── Main ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`Environment: ${IS_PRELIVE ? "pre-live" : "production"}`);
  console.log("Fetching Surah Al-Fatiha…");

  const { verses } = await apiGet("/quran/verses/uthmani_tajweed", {
    chapter_number: 2,
  });

  const html = buildHtml(verses);
  const outPath = path.join(__dirname, "fatiha.html");
  fs.writeFileSync(outPath, html, "utf8");

  console.log(`\n✓ Saved → ${outPath}`);
  console.log(
    "  Open fatiha.html in your browser to view the color-coded tajweed.\n",
  );
}

main().catch((err) => {
  console.error("✗", err.message);
  if (err.body) console.error("  Response:", err.body);
  process.exit(1);
});
