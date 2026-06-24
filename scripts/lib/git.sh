#!/usr/bin/env bash
# lib/git.sh — git workflow helpers for the release pipeline.
# Source this file; do not execute directly.

# git::current_branch → prints current branch name
git::current_branch() {
    git rev-parse --abbrev-ref HEAD
}

# git::ensure_clean
# Aborts if there are uncommitted changes (staged or unstaged).
git::ensure_clean() {
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "✗ Working tree has uncommitted changes — stash or commit them first."
        exit 1
    fi
}

# git::sync_branch <branch>
# Fetches and fast-forwards a local branch to its remote counterpart.
git::sync_branch() {
    local branch="$1"
    echo "▶ Syncing ${branch} with origin…"
    git fetch origin "$branch" --quiet
    git checkout "$branch" --quiet
    git merge --ff-only "origin/${branch}" --quiet \
        || { echo "✗ Cannot fast-forward ${branch} — diverged from origin. Resolve manually."; exit 1; }
}

# git::merge_to_main <source_branch> <main_branch>
# Merges source_branch into main (fast-forward preferred, merge commit fallback).
git::merge_to_main() {
    local src="$1" main="$2"
    echo "▶ Merging ${src} → ${main}…"
    git checkout "$main" --quiet
    git merge --no-edit "$src" --quiet \
        || { echo "✗ Merge conflict: ${src} → ${main}. Resolve manually then re-run."; exit 1; }
}

# git::commit_and_push <branch> <message> [files...]
# Stages the given files, commits, and pushes to origin.
git::commit_and_push() {
    local branch="$1" msg="$2"; shift 2
    git add "$@"
    if ! git diff --cached --quiet; then
        git commit -m "$msg" --quiet
        echo "▶ Committed: ${msg}"
    fi
    git push origin "$branch" --quiet
    echo "▶ Pushed ${branch} to origin"
}

# git::rebase_on_main <source_branch> <main_branch>
# Rebases source_branch on top of main, then pushes (force-with-lease for safety).
git::rebase_on_main() {
    local src="$1" main="$2"
    echo "▶ Rebasing ${src} on ${main}…"
    git checkout "$src" --quiet
    git rebase "$main" --quiet \
        || { echo "✗ Rebase conflict on ${src}. Resolve manually: git rebase --continue"; exit 1; }
    git push origin "$src" --force-with-lease --quiet
    echo "▶ ${src} rebased and pushed"
}

# git::tag_exists <tag>
git::tag_exists() {
    gh release view "$1" >/dev/null 2>&1
}