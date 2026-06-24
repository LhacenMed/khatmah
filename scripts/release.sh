#!/usr/bin/env bash
#
# release.sh — smart release pipeline for Khatmah.
#
# Run from ANY branch (main or dev or feature/*).
# The script will:
#   1. Read the current version from app/build.gradle.kts
#   2. Prompt for bump type (major / minor / patch) and optional pre-release flavour
#   3. If not on main: sync dev, merge dev → main, perform the bump on main
#   4. Build signed release APKs
#   5. Write version.json, commit + push main
#   6. Create the GitHub release and upload every APK variant
#   7. Rebase the source branch back on top of main (so dev stays in sync)
#
# Usage:
#   ./scripts/release.sh
#
# Requirements: gh (authenticated), jq, perl, and keystore.properties present.

set -euo pipefail

# ── Bootstrap ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

source "${SCRIPT_DIR}/lib/version.sh"
source "${SCRIPT_DIR}/lib/git.sh"

GRADLE_FILE="app/build.gradle.kts"
MAIN_BRANCH="main"
RELEASE_DIR="app/build/outputs/apk/release"

# ── Preconditions ───────────────────────────────────────────────────────────────
for cmd in gh jq perl; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }
[ -f keystore.properties ] || { echo "✗ keystore.properties missing — release APK would be unsigned"; exit 1; }
[ -f "$GRADLE_FILE"       ] || { echo "✗ ${GRADLE_FILE} not found"; exit 1; }

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
SOURCE_BRANCH="$(git::current_branch)"

# ── Guard: no uncommitted changes ───────────────────────────────────────────────
git::ensure_clean

# ── Step 1: Read current version ────────────────────────────────────────────────
version::read "$GRADLE_FILE"
CURRENT_NAME="$(version::name "$V_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│           Khatmah Release Pipeline           │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Current version : ${CURRENT_NAME}  (${V_TYPE})"
echo "  Source branch   : ${SOURCE_BRANCH}"
echo "  Target branch   : ${MAIN_BRANCH}"
echo ""

# ── Step 2: Choose version type ─────────────────────────────────────────────────
echo "  Release type:"
echo "    1) Stable          (e.g. 1.2.3)"
echo "    2) Alpha           (e.g. 1.2.3-alpha.1)"
echo "    3) Beta            (e.g. 1.2.3-beta.1)"
echo "    4) Release Candidate  (e.g. 1.2.3-rc.1)"
echo ""
read -rp "  Select [1-4, default 1]: " TYPE_CHOICE
TYPE_CHOICE="${TYPE_CHOICE:-1}"

case "$TYPE_CHOICE" in
    1) NEW_TYPE="Stable" ;;
    2) NEW_TYPE="Alpha" ;;
    3) NEW_TYPE="Beta" ;;
    4) NEW_TYPE="ReleaseCandidate" ;;
    *) echo "✗ Invalid choice"; exit 1 ;;
esac

# ── Step 3: Choose bump kind ────────────────────────────────────────────────────
echo ""
echo "  Version bump:"
echo "    1) patch  — bug fixes          (x.y.Z)"
echo "    2) minor  — new features       (x.Y.0)"
echo "    3) major  — breaking changes   (X.0.0)"

# Pre-release types also offer build-number increment
if [[ "$NEW_TYPE" != "Stable" ]]; then
    echo "    4) build  — pre-release iteration (same x.y.z, +build)"
fi

echo ""
BUMP_EXTRA=""; [[ "$NEW_TYPE" != "Stable" ]] && BUMP_EXTRA=" or 4"
read -rp "  Select [1-3${BUMP_EXTRA}, default 1]: " BUMP_CHOICE
BUMP_CHOICE="${BUMP_CHOICE:-1}"

case "$BUMP_CHOICE" in
    1) BUMP_KIND="patch" ;;
    2) BUMP_KIND="minor" ;;
    3) BUMP_KIND="major" ;;
    4)
        if [[ "$NEW_TYPE" == "Stable" ]]; then
            echo "✗ Build increment is only for pre-release types"; exit 1
        fi
        BUMP_KIND="build"
        ;;
    *) echo "✗ Invalid choice"; exit 1 ;;
esac

# Apply bump to current values (modifies V_MAJOR, V_MINOR, V_PATCH, V_BUILD in place)
version::bump "$NEW_TYPE" "$BUMP_KIND"
NEW_NAME="$(version::name "$NEW_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"
TAG="v${NEW_NAME}"

# ── Step 4: Release notes ────────────────────────────────────────────────────────
echo ""
read -rp "  Release notes [Bug fixes and improvements.]: " NOTES
NOTES="${NOTES:-Bug fixes and improvements.}"

# ── Step 5: Preview + confirm ────────────────────────────────────────────────────
echo ""
echo "  ┌── Release Preview ────────────────────────────┐"
echo "  │  ${CURRENT_NAME}  →  ${NEW_NAME}"
echo "  │  Tag    : ${TAG}"
echo "  │  Type   : ${NEW_TYPE}"
echo "  │  Notes  : ${NOTES}"
echo "  │  Repo   : ${REPO}"
echo "  └───────────────────────────────────────────────┘"
echo ""

if git::tag_exists "$TAG"; then
    echo "✗ Release ${TAG} already exists on GitHub — bump to a different version."; exit 1
fi

read -rp "  Proceed? [y/N]: " CONFIRM
[[ "${CONFIRM,,}" == "y" ]] || { echo "  Aborted."; exit 0; }

# ── Step 6: Git workflow — merge source branch into main ─────────────────────────
if [[ "$SOURCE_BRANCH" != "$MAIN_BRANCH" ]]; then
    echo ""
    echo "▶ Preparing branches…"
    git::sync_branch "$SOURCE_BRANCH"
    git::sync_branch "$MAIN_BRANCH"
    git::merge_to_main "$SOURCE_BRANCH" "$MAIN_BRANCH"
else
    echo ""
    echo "▶ Already on ${MAIN_BRANCH} — syncing…"
    git::sync_branch "$MAIN_BRANCH"
fi

# ── Step 7: Bump version in build.gradle.kts on main ────────────────────────────
echo "▶ Bumping version to ${NEW_NAME} in ${GRADLE_FILE}…"
version::write "$GRADLE_FILE" "$NEW_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD"

# Verify the write round-trips correctly
version::read "$GRADLE_FILE"
VERIFY_NAME="$(version::name "$V_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"
if [[ "$VERIFY_NAME" != "$NEW_NAME" ]]; then
    echo "✗ Version write verification failed (got ${VERIFY_NAME}, expected ${NEW_NAME})"; exit 1
fi
echo "  ✓ Verified: ${VERIFY_NAME}"

# ── Step 8: Build signed release APKs ───────────────────────────────────────────
echo ""
echo "▶ Building signed release APK(s)…"
./gradlew clean :app:assembleRelease

# ── Step 9: Read real version from build output ──────────────────────────────────
META="${RELEASE_DIR}/output-metadata.json"
[ -f "$META" ] || { echo "✗ ${META} not found — did the build produce a release APK?"; exit 1; }

ELEM="$(jq -c 'first(.elements[] | select(.filters == [])) // .elements[0]' "$META")"
VERSION_CODE="$(jq -r '.versionCode' <<<"$ELEM")"
UNIVERSAL_APK="${RELEASE_DIR}/$(jq -r '.outputFile' <<<"$ELEM")"
[ -f "$UNIVERSAL_APK" ] || { echo "✗ Universal APK not found at ${UNIVERSAL_APK}"; exit 1; }

APK_URL="https://github.com/${REPO}/releases/download/${TAG}/$(basename "$UNIVERSAL_APK")"

# Collect all APK variants (universal + per-ABI)
APKS=()
while IFS= read -r f; do APKS+=("$f"); done \
    < <(ls -1 "${RELEASE_DIR}/khatmah-${NEW_NAME}-"*.apk 2>/dev/null)
[ "${#APKS[@]}" -gt 0 ] || { echo "✗ No APKs matched khatmah-${NEW_NAME}-*.apk"; exit 1; }
echo "  ✓ ${#APKS[@]} APK(s): $(printf '%s ' "${APKS[@]##*/}")"

# ── Step 10: Write version.json ──────────────────────────────────────────────────
jq -n \
    --argjson versionCode "$VERSION_CODE" \
    --arg versionName     "$NEW_NAME" \
    --arg apkUrl          "$APK_URL" \
    --arg notes           "$NOTES" \
    '{versionCode: $versionCode, versionName: $versionName, apkUrl: $apkUrl, notes: $notes}' \
    > version.json
echo "▶ Wrote version.json"

# ── Step 11: Commit version bump + manifest, push main ───────────────────────────
git::commit_and_push "$MAIN_BRANCH" "release: ${TAG}" "$GRADLE_FILE" version.json

# ── Step 12: Create GitHub release, upload APKs ──────────────────────────────────
echo "▶ Creating GitHub release ${TAG}…"
gh release create "$TAG" \
    --target "$MAIN_BRANCH" \
    --title  "$TAG" \
    --notes  "$NOTES" \
    "${APKS[@]}"

# ── Step 13: Rebase source branch on main (keep dev in sync) ─────────────────────
if [[ "$SOURCE_BRANCH" != "$MAIN_BRANCH" ]]; then
    git::rebase_on_main "$SOURCE_BRANCH" "$MAIN_BRANCH"
fi

# ── Done ─────────────────────────────────────────────────────────────────────────
echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│              ✓ Release complete!             │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Version   : ${NEW_NAME}"
echo "  Tag       : ${TAG}"
echo "  APK URL   : ${APK_URL}"
echo "  Manifest  : https://raw.githubusercontent.com/${REPO}/${MAIN_BRANCH}/version.json"
echo "  Branch    : back on ${SOURCE_BRANCH} (rebased on ${MAIN_BRANCH})"
echo ""