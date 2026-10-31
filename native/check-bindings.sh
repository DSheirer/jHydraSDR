#!/usr/bin/env bash
# Compares the functions in hydrasdr.h with the bindings in Native.java, so a
# new upstream function fails the build rather than being silently unavailable.
#
# Usage: native/check-bindings.sh <path-to-hydrasdr.h>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HEADER="$1"
BINDINGS="$ROOT/src/main/java/io/github/dsheirer/hydrasdr/internal/Native.java"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Declarations look like: extern ADDAPI [HYDRASDR_DEPRECATED("...")] int ADDCALL name(
grep -E '^extern ADDAPI' "$HEADER" > "$WORK/declarations"
grep -v 'HYDRASDR_DEPRECATED' "$WORK/declarations" \
    | sed -E 's/.*ADDCALL ([a-z0-9_]+)\(.*/\1/' | sort -u > "$WORK/header"
grep -oE '"hydrasdr_[a-z0-9_]+"' "$BINDINGS" | tr -d '"' | sort -u > "$WORK/bindings"

MISSING="$(comm -23 "$WORK/header" "$WORK/bindings")"
STALE="$(comm -13 "$WORK/header" "$WORK/bindings")"
if [ -n "$MISSING" ] || [ -n "$STALE" ]; then
    [ -z "$MISSING" ] || printf 'Functions in hydrasdr.h without a binding:\n%s\n' "$MISSING" >&2
    [ -z "$STALE" ] || printf 'Bindings not in hydrasdr.h:\n%s\n' "$STALE" >&2
    exit 1
fi
echo "Bindings match hydrasdr.h ($(wc -l < "$WORK/header" | tr -d ' ') functions)"
