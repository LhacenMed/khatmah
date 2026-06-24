#!/usr/bin/env bash
# lib/version.sh — version read/bump helpers for build.gradle.kts.
# Source this file; do not execute directly.

# ── Regex patterns (match the sealed class blocks in build.gradle.kts) ─────────
_VERSION_CLASS_RE='Version\.(Stable|Alpha|Beta|ReleaseCandidate)\('
_MAJOR_RE='versionMajor[[:space:]]*=[[:space:]]*([0-9]+)'
_MINOR_RE='versionMinor[[:space:]]*=[[:space:]]*([0-9]+)'
_PATCH_RE='versionPatch[[:space:]]*=[[:space:]]*([0-9]+)'
_BUILD_RE='versionBuild[[:space:]]*=[[:space:]]*([0-9]+)'

# version::read <gradle_file>
# Sets: V_TYPE, V_MAJOR, V_MINOR, V_PATCH, V_BUILD
version::read() {
    local file="$1"
    local block
    block="$(awk '/val currentVersion/,/\)/' "$file")"

    if   echo "$block" | grep -q 'Version\.Alpha';             then V_TYPE="Alpha"
    elif echo "$block" | grep -q 'Version\.Beta';              then V_TYPE="Beta"
    elif echo "$block" | grep -q 'Version\.ReleaseCandidate';  then V_TYPE="ReleaseCandidate"
    else                                                             V_TYPE="Stable"
    fi

    V_MAJOR="$(echo "$block" | grep -oE "$_MAJOR_RE" | grep -oE '[0-9]+$')"
    V_MINOR="$(echo "$block" | grep -oE "$_MINOR_RE" | grep -oE '[0-9]+$')"
    V_PATCH="$(echo "$block" | grep -oE "$_PATCH_RE" | grep -oE '[0-9]+$')"
    V_BUILD="$(echo "$block" | grep -oE "$_BUILD_RE" | grep -oE '[0-9]+$')"
    V_BUILD="${V_BUILD:-0}"
}

# version::name <type> <major> <minor> <patch> [build]
# Prints the human-readable version string (mirrors Kotlin toVersionName()).
version::name() {
    local type="$1" major="$2" minor="$3" patch="$4" build="${5:-0}"
    case "$type" in
        Alpha)            echo "${major}.${minor}.${patch}-alpha.${build}" ;;
        Beta)             echo "${major}.${minor}.${patch}-beta.${build}"  ;;
        ReleaseCandidate) echo "${major}.${minor}.${patch}-rc.${build}"    ;;
        *)                echo "${major}.${minor}.${patch}"                ;;
    esac
}

# version::bump <type> <bump_kind>
# bump_kind: major | minor | patch | build (build increments versionBuild for pre-release)
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_BUILD (in place)
version::bump() {
    local type="$1" kind="$2"
    case "$kind" in
        major) V_MAJOR=$(( V_MAJOR + 1 )); V_MINOR=0; V_PATCH=0; V_BUILD=0 ;;
        minor) V_MINOR=$(( V_MINOR + 1 )); V_PATCH=0; V_BUILD=0             ;;
        patch) V_PATCH=$(( V_PATCH + 1 )); V_BUILD=0                        ;;
        build) V_BUILD=$(( V_BUILD + 1 ))                                   ;;
    esac
}

# version::write <gradle_file> <new_type> <major> <minor> <patch> [build]
# Rewrites the `val currentVersion` block in-place.
version::write() {
    local file="$1" type="$2" major="$3" minor="$4" patch="$5" build="${6:-0}"

    local has_build=""
    [[ "$type" != "Stable" ]] && has_build="    versionBuild = ${build},"

    # Build the replacement block (indentation matches project style).
    local new_block
    if [[ "$type" == "Stable" ]]; then
        new_block="val currentVersion: Version = Version.Stable(\n    versionMajor = ${major},\n    versionMinor = ${minor},\n    versionPatch = ${patch},\n)"
    else
        local class_name="$type"
        new_block="val currentVersion: Version = Version.${class_name}(\n    versionMajor = ${major},\n    versionMinor = ${minor},\n    versionPatch = ${patch},\n    versionBuild = ${build},\n)"
    fi

    # Use perl for reliable multi-line substitution (sed -z is not universal on macOS/Git Bash).
    perl -i -0777 -pe \
        "s/val currentVersion: Version = Version\.\w+\(.*?\)\n?/${new_block}\n/s" \
        "$file"
}