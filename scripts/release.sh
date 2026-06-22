#!/usr/bin/env bash
#
# release.sh — build, publish and announce a new Khatmah release.
#
# The version is NOT passed in: app/build.gradle.kts is the single source of truth. This script
# builds the signed release APKs, reads the real versionName/versionCode straight from the build's
# output-metadata.json, then derives the git tag and version.json from it — so the in-app update
# manifest can never drift from the APK users actually install.
#
# Usage:
#   1. Bump `currentVersion` in app/build.gradle.kts (e.g. versionPatch 0 -> 1).
#   2. ./scripts/release.sh "Release notes shown in the dialog"
#
# Steps performed:
#   build (signed) -> read metadata -> write version.json -> commit & push main
#   -> create GitHub release vX.Y.Z -> upload every APK variant (universal + per-ABI).
#
# Requirements: gh (authenticated), jq, and keystore.properties present (for signing).

set -euo pipefail

# ── Locate repo root (this script lives in <root>/scripts) ─────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

NOTES="${1:-Bug fixes and improvements.}"

# ── Preconditions ──────────────────────────────────────────────────────────────
command -v gh >/dev/null || { echo "✗ gh CLI not found"; exit 1; }
command -v jq >/dev/null || { echo "✗ jq not found"; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated (run: gh auth login)"; exit 1; }
[ -f keystore.properties ] || { echo "✗ keystore.properties missing — release APK would be unsigned"; exit 1; }

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"   # e.g. LhacenMed/khatmah
BRANCH="main"

# ── 1. Build the signed release APK ────────────────────────────────────────────
echo "▶ Building signed release APK…"
./gradlew clean :app:assembleRelease

# ── 2. Read the real version from the build output ─────────────────────────────
META="app/build/outputs/apk/release/output-metadata.json"
[ -f "$META" ] || { echo "✗ $META not found — did the build produce a release APK?"; exit 1; }

# Version metadata comes from the universal output (the all-ABI, sideloadable APK). When ABI
# splits are enabled the build emits several per-ABI APKs alongside it; pick the element with no
# ABI filter rather than trusting array order.
RELEASE_DIR="app/build/outputs/apk/release"
ELEM="$(jq -c 'first(.elements[] | select(.filters == [])) // .elements[0]' "$META")"
VERSION_NAME="$(jq -r '.versionName' <<<"$ELEM")"
VERSION_CODE="$(jq -r '.versionCode' <<<"$ELEM")"
UNIVERSAL_APK="${RELEASE_DIR}/$(jq -r '.outputFile' <<<"$ELEM")"
[ -f "$UNIVERSAL_APK" ] || { echo "✗ universal APK not found at $UNIVERSAL_APK"; exit 1; }

TAG="v${VERSION_NAME}"
echo "▶ Version ${VERSION_NAME} (code ${VERSION_CODE}) → tag ${TAG}"

# Refuse to clobber an existing release/tag.
if gh release view "$TAG" >/dev/null 2>&1; then
    echo "✗ Release $TAG already exists — bump the version in build.gradle.kts first."; exit 1
fi

# Every APK this version produced (universal + any per-ABI splits) becomes a release asset.
APKS=()
while IFS= read -r f; do APKS+=("$f"); done \
    < <(ls -1 "${RELEASE_DIR}/khatmah-${VERSION_NAME}-"*.apk)
[ "${#APKS[@]}" -gt 0 ] || { echo "✗ no APKs matched khatmah-${VERSION_NAME}-*.apk"; exit 1; }
echo "▶ ${#APKS[@]} APK(s) to upload: $(printf '%s ' "${APKS[@]##*/}")"

# The in-app updater always downloads the universal APK (installs on any device).
APK_URL="https://github.com/${REPO}/releases/download/${TAG}/$(basename "$UNIVERSAL_APK")"

# ── 3. Regenerate the in-app update manifest from the build output ──────────────
jq -n \
    --argjson versionCode "$VERSION_CODE" \
    --arg versionName "$VERSION_NAME" \
    --arg apkUrl "$APK_URL" \
    --arg notes "$NOTES" \
    '{versionCode: $versionCode, versionName: $versionName, apkUrl: $apkUrl, notes: $notes}' \
    > version.json
echo "▶ Wrote version.json"

# ── 4. Commit the version bump + manifest and push main ────────────────────────
git add app/build.gradle.kts version.json
if ! git diff --cached --quiet; then
    git commit -m "release: ${TAG}"
    echo "▶ Committed release ${TAG}"
else
    echo "▶ Nothing to commit (build.gradle.kts / version.json unchanged)"
fi
git push origin "$BRANCH"

# ── 5. Create the GitHub release and upload every APK variant ──────────────────
echo "▶ Creating GitHub release ${TAG}…"
gh release create "$TAG" \
    --target "$BRANCH" \
    --title "$TAG" \
    --notes "$NOTES" \
    "${APKS[@]}"

echo "✓ Released ${TAG}"
echo "  Update APK: ${APK_URL}"
echo "  Manifest:   https://raw.githubusercontent.com/${REPO}/${BRANCH}/version.json"
