#!/usr/bin/env bash
# Builds "Vazie VPN.app" from the Swift package.
#
#   scripts/build-app.sh [--release] [--version 1.0.0] [--build 1] [--identity "Developer ID Application: …"] [--no-engine] [--out DIR]
#
# The Xray engine is downloaded once (cached), checked against the SHA-256 pinned in Sources/VazieCore/Engine.swift,
# and placed inside the app, so the first launch needs no download. Without --identity the app is signed with the
# first "Apple Development" identity in the keychain, else ad hoc. Notarization is the owner's step.
#
# The root supervisor (packaging/tun-run.sh) is NOT copied into the bundle: it is carried inside the signed executable
# (Sources/VazieCore/TunScriptSource.swift, generated below), so nothing in the bundle is ever run as root. With a signing
# identity the engine is re-signed with it and the supervisor accepts it by the team; without one the engine is left as
# upstream built it and the supervisor accepts it by its pinned SHA-256 (Engine.pinnedBinaries).
#
# --out DIR builds into DIR instead of ./build (so an app that is running from ./build is not replaced under it).
set -euo pipefail

cd "$(dirname "$0")/.."
CONFIG=debug
VERSION=1.0.0
BUILD=1
IDENTITY=""
ENGINE=1
OUTDIR=build
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) CONFIG=release ;;
    --version) VERSION="$2"; shift ;;
    --build) BUILD="$2"; shift ;;
    --identity) IDENTITY="$2"; shift ;;
    --no-engine) ENGINE=0 ;;
    --out) OUTDIR="$2"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

OUT="$OUTDIR/Vazie VPN.app"
scripts/embed-tun-script.sh
swift build -c "$CONFIG"
BIN_DIR="$(swift build -c "$CONFIG" --show-bin-path)"
[[ -x "$BIN_DIR/VazieVPN" ]] || { echo "no executable at $BIN_DIR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/Contents/MacOS" "$OUT/Contents/Resources"
cp "$BIN_DIR/VazieVPN" "$OUT/Contents/MacOS/VazieVPN"
for bundle in "$BIN_DIR"/*.bundle; do cp -R "$bundle" "$OUT/Contents/Resources/"; done
# The official backend and site addresses are not in the repository: VAZIE_API_URL and VAZIE_SITE_URL give them.
# Empty means the app falls back to a placeholder (Config in BackendData.swift), fine for development and CI.
API_URL="${VAZIE_API_URL:-}"; SITE_URL="${VAZIE_SITE_URL:-}"
if [[ "$CONFIG" == release && ( -z "$API_URL" || -z "$SITE_URL" ) ]]; then
  echo "warning: VAZIE_API_URL / VAZIE_SITE_URL are not set: this app will not reach the Vazie backend" >&2
fi
escape() { printf '%s' "$1" | sed -e 's/[&|\\]/\\&/g'; }
sed -e "s/__VERSION__/$VERSION/" -e "s/__BUILD__/$BUILD/" \
    -e "s|__API_URL__|$(escape "$API_URL")|" -e "s|__SITE_URL__|$(escape "$SITE_URL")|" \
    packaging/Info.plist > "$OUT/Contents/Info.plist"

# The icon.
TMP="$(mktemp -d)"
swiftc scripts/make-icns.swift -o "$TMP/make-icns"
"$TMP/make-icns" Sources/VazieVPN/Resources/marks/orbit.png "$TMP/AppIcon.iconset"
iconutil -c icns "$TMP/AppIcon.iconset" -o "$OUT/Contents/Resources/AppIcon.icns"

# The engine, verified.
if [[ $ENGINE -eq 1 ]]; then
  VERSION_TAG="$(grep -E 'static let version = "v' Sources/VazieCore/Engine.swift | sed -E 's/.*"(v[^"]+)".*/\1/')"
  case "$(uname -m)" in
    arm64) FILE="Xray-macos-arm64-v8a.zip" ;;
    *) FILE="Xray-macos-64.zip" ;;
  esac
  SHA="$(grep -E "file: \"$FILE\"" Sources/VazieCore/Engine.swift | sed -E 's/.*sha256: "([0-9a-f]+)".*/\1/')"
  CACHE="$HOME/Library/Caches/VazieVPN-build"
  mkdir -p "$CACHE"
  ZIP="$CACHE/$VERSION_TAG-$FILE"
  if [[ ! -f "$ZIP" ]]; then
    curl -fsSL -o "$ZIP.part" "https://github.com/XTLS/Xray-core/releases/download/$VERSION_TAG/$FILE"
    mv "$ZIP.part" "$ZIP"
  fi
  ACTUAL="$(shasum -a 256 "$ZIP" | cut -d' ' -f1)"
  [[ "$ACTUAL" == "$SHA" ]] || { echo "the engine's SHA-256 is $ACTUAL, not the pinned $SHA" >&2; rm -f "$ZIP"; exit 1; }
  mkdir -p "$OUT/Contents/Resources/engine"
  unzip -o -j -q "$ZIP" xray -d "$OUT/Contents/Resources/engine"
  chmod 755 "$OUT/Contents/Resources/engine/xray"
fi

# Signing: the nested engine first, then the app.
if [[ -z "$IDENTITY" ]]; then
  IDENTITY="$(security find-identity -v -p codesigning | sed -n 's/.*"\(Apple Development:[^"]*\)".*/\1/p' | head -1)"
fi
SIGN=(--force --timestamp=none)
if [[ -n "$IDENTITY" ]]; then SIGN+=(--sign "$IDENTITY"); else SIGN+=(--sign -); fi
# An ad hoc build keeps the engine's upstream signature so that its pinned hash still matches.
[[ -n "$IDENTITY" && -f "$OUT/Contents/Resources/engine/xray" ]] && codesign "${SIGN[@]}" "$OUT/Contents/Resources/engine/xray"
for bundle in "$OUT"/Contents/Resources/*.bundle; do codesign "${SIGN[@]}" "$bundle"; done
codesign "${SIGN[@]}" --options runtime "$OUT"
codesign --verify --deep --strict "$OUT"
echo "built $OUT (${IDENTITY:-ad hoc})"
