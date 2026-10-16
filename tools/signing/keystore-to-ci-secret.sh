#!/usr/bin/env bash

set -euo pipefail

KEYSTORE="${1:-}"
[ -n "$KEYSTORE" ] || { echo "usage: $0 <keystore-file>" >&2; exit 2; }
[ -f "$KEYSTORE" ] || { echo "no such keystore: $KEYSTORE" >&2; exit 2; }

# `base64 -w0` on GNU, no wrapping flag on BSD/macOS where it is already one line per read.
if base64 --help 2>&1 | grep -q -- "-w"; then
    base64 -w0 < "$KEYSTORE"
else
    base64 < "$KEYSTORE" | tr -d '\n'
fi
printf '\n'
