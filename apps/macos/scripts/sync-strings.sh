#!/usr/bin/env bash
# Copies the words the Mac app shares with the other clients: Android's (already imported into the extension by
# scripts/import-strings.mjs there) and the extension's own. The Mac's own words are mac.{en,ru}.json and are
# edited by hand. `--check` fails if the copies are out of date.
set -euo pipefail
cd "$(dirname "$0")/.."
SRC=../browser-extension/src/shared/i18n
DST=Sources/VazieCore/Resources/i18n
for f in android.en android.ru ext.en ext.ru; do
  if [[ "${1:-}" == "--check" ]]; then
    diff -q "$SRC/$f.json" "$DST/$f.json" >/dev/null || { echo "$f.json is out of step; run scripts/sync-strings.sh" >&2; exit 1; }
  else
    cp "$SRC/$f.json" "$DST/$f.json"
  fi
done
