#!/usr/bin/env bash
# Builds the installer: build/VazieVPN-macOS-<version>.dmg, a window with the app and a shortcut to Applications.
#
#   scripts/make-dmg.sh [--version 1.0.0] [--build 1] [--identity "Developer ID Application: …"] [--no-build]
#
# Builds the app first (scripts/build-app.sh --release) unless --no-build. The disk image is signed with the same
# identity when one is found; notarizing it (and stapling the ticket) is the owner's step:
#   xcrun notarytool submit build/VazieVPN-macOS-<version>.dmg --keychain-profile <profile> --wait && xcrun stapler staple …
set -euo pipefail

cd "$(dirname "$0")/.."
VERSION=1.0.0
BUILD=1
IDENTITY=""
BUILD_APP=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift ;;
    --build) BUILD="$2"; shift ;;
    --identity) IDENTITY="$2"; shift ;;
    --no-build) BUILD_APP=0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

APP="build/Vazie VPN.app"
if [[ $BUILD_APP -eq 1 ]]; then
  scripts/build-app.sh --release --version "$VERSION" --build "$BUILD" ${IDENTITY:+--identity "$IDENTITY"}
fi
[[ -d "$APP" ]] || { echo "no $APP; run scripts/build-app.sh" >&2; exit 1; }

NAME="Vazie VPN"
FINAL="build/VazieVPN-macOS-$VERSION.dmg"
TMP="$(mktemp -d)"
STAGE="$TMP/stage"
RW="$TMP/rw.dmg"
trap 'hdiutil detach "/Volumes/$NAME" -force >/dev/null 2>&1 || true; rm -rf "$TMP"' EXIT

# The window's background, in the language of the Mac that builds it.
case "$(defaults read -g AppleLanguages 2>/dev/null | tr -d '() \n"' | cut -c1-2)" in
  ru) LINE="Перетащите Vazie VPN в «Программы»" ;;
  *) LINE="Drag Vazie VPN to Applications" ;;
esac
mkdir -p "$STAGE/.background"
swiftc scripts/make-dmg-background.swift -o "$TMP/dmg-bg"
"$TMP/dmg-bg" Sources/VazieVPN/Resources/fonts/geologica.ttf packaging/dmg-background-art.png "$STAGE/.background/background.png" "$LINE"

ditto "$APP" "$STAGE/$NAME.app"
ln -s /Applications "$STAGE/Applications"

rm -f "$FINAL" "$RW"
SIZE_MB=$(( $(du -sm "$STAGE" | cut -f1) + 20 ))
hdiutil create -quiet -srcfolder "$STAGE" -volname "$NAME" -fs HFS+ -format UDRW "$RW"
hdiutil detach "/Volumes/$NAME" -force >/dev/null 2>&1 || true
hdiutil attach -quiet -noautoopen -readwrite "$RW" >/dev/null
VOLUME="/Volumes/$NAME"

# A Finder alias, not a symlink: this Finder does not keep the position of a symlink.
rm -f "$VOLUME/Applications"
osascript -e "tell application \"Finder\" to make new alias file to (POSIX file \"/Applications\" as alias) at (POSIX file \"$VOLUME\" as alias) with properties {name:\"Applications\"}" >/dev/null

# The one hidden folder is flagged before Finder lays the window out: anything changed in the volume afterwards
# makes Finder write its default layout over the one just recorded.
SetFile -a V "$VOLUME/.background" 2>/dev/null || true

# Icon view, the two icons where the arrow says, no toolbar. Finder remembers it in the volume's .DS_Store, but only
# if it is told slowly (each step gets a moment) and the window is closed at the end. It is checked in the file, and
# tried again if Finder was too quick.
layout_recorded() {
  python3 - "${1:-$VOLUME}/.DS_Store" <<'PY'
import plistlib, sys
try:
    data = open(sys.argv[1], "rb").read()
except OSError:
    sys.exit(1)
size, i = None, 0
while True:
    i = data.find(b"bplist00", i)
    if i < 0:
        break
    for n in range(60, 3000):
        try:
            size = plistlib.loads(data[i:i + n]).get("iconSize", size)
            break
        except Exception:
            pass
    i += 8
sys.exit(0 if size == 128.0 and b"backgroundImageAlias" in data else 1)
PY
}
for attempt in 1 2 3; do
  osascript <<OSA
tell application "Finder"
  tell disk "$NAME"
    open
    delay 2
    set current view of container window to icon view
    delay 1
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {200, 120, 860, 548}
    set opts to the icon view options of container window
    set arrangement of opts to not arranged
    set icon size of opts to 128
    set text size of opts to 13
    delay 1
    set background picture of opts to (POSIX file "$VOLUME/.background/background.png" as alias)
    delay 1
    set position of item "$NAME.app" of container window to {180, 225}
    delay 1
    set position of item "Applications" of container window to {480, 225}
    delay 1
    -- For a person who has Finder show hidden files: the folder goes into a corner of the sky.
    try
      set position of item ".background" of container window to {70, 70}
    end try
    delay 1
    delay 3
    close
  end tell
end tell
OSA
  for _ in $(seq 1 8); do sync; layout_recorded && break 2; sleep 1; done
  echo "Finder was too quick (attempt $attempt); trying again" >&2
done
layout_recorded || { echo "Finder did not record the installer window's layout" >&2; exit 1; }
# Finder writes its default layout over this one when the volume is unmounted, so the recorded file is kept aside and
# put back on a mount Finder does not know about.
cp "$VOLUME/.DS_Store" "$TMP/DS_Store.recorded"
sync; sleep 1
hdiutil detach "$VOLUME" -quiet || { sleep 2; hdiutil detach "$VOLUME" -force -quiet; }
hdiutil attach -quiet -readwrite -nobrowse -noautoopen -mountpoint "$TMP/mnt" "$RW" 2>/dev/null || { mkdir -p "$TMP/mnt"; hdiutil attach -quiet -readwrite -nobrowse -noautoopen -mountpoint "$TMP/mnt" "$RW"; }
cp "$TMP/DS_Store.recorded" "$TMP/mnt/.DS_Store"
# macOS adds these on every mount; a person who shows hidden files should not see them in the installer.
rm -rf "$TMP/mnt/.fseventsd" "$TMP/mnt/.Trashes" "$TMP/mnt/.Spotlight-V100" 2>/dev/null || true
sync
hdiutil detach "$TMP/mnt" -quiet

hdiutil convert "$RW" -quiet -format UDZO -imagekey zlib-level=9 -o "$FINAL"

# What ships is what was checked: the finished image's own layout file, read back.
CHECK="$TMP/check"
mkdir -p "$CHECK"
hdiutil attach -quiet -readonly -nobrowse -noautoopen -mountpoint "$CHECK" "$FINAL"
layout_recorded "$CHECK" || { hdiutil detach "$CHECK" -quiet -force; echo "the finished image lost its window layout" >&2; exit 1; }
hdiutil detach "$CHECK" -quiet

if [[ -z "$IDENTITY" ]]; then
  IDENTITY="$(security find-identity -v -p codesigning | sed -n 's/.*"\(Apple Development:[^"]*\)".*/\1/p' | head -1)"
fi
if [[ -n "$IDENTITY" && "$IDENTITY" != "-" ]]; then codesign --force --sign "$IDENTITY" "$FINAL"; fi
hdiutil verify -quiet "$FINAL"
echo "built $FINAL ($(du -h "$FINAL" | cut -f1))"
shasum -a 256 "$FINAL"
