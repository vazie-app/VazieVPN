#!/bin/bash
# Builds VazieVPNHelper.pkg: what an ordinary person opens to install the helper, with nothing to type.
#
#   packaging/macos/build-pkg.sh [output-dir]
#
# A universal binary (Apple silicon and Intel) when both Rust targets are installed, otherwise this machine's own.
# Unsigned unless the owner's identities are in the environment (they are not in this repository):
#   APP_SIGN_IDENTITY   "Developer ID Application: …"   signs the binary (hardened runtime, timestamp)
#   PKG_SIGN_IDENTITY   "Developer ID Installer: …"     signs the package
#   NOTARY_PROFILE      a `notarytool store-credentials` profile   notarizes and staples the package
set -euo pipefail
# No AppleDouble (`._*`) files in the package.
export COPYFILE_DISABLE=1

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CRATE="$(cd "$HERE/../.." && pwd)"
OUT="${1:-$CRATE/target/pkg}"
VERSION="$(sed -n 's/^version = "\(.*\)"/\1/p' "$CRATE/Cargo.toml" | head -n1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cd "$CRATE"
ARM=aarch64-apple-darwin
X86=x86_64-apple-darwin
installed="$(rustup target list --installed 2>/dev/null || true)"
BINARIES=()
for target in "$ARM" "$X86"; do
  if grep -qx "$target" <<<"$installed"; then
    cargo build --release --locked --target "$target"
    BINARIES+=("target/$target/release/vazie-vpn-host")
  fi
done
if [ ${#BINARIES[@]} -eq 0 ]; then
  cargo build --release --locked
  BINARIES+=("target/release/vazie-vpn-host")
fi
if [ ${#BINARIES[@]} -lt 2 ]; then
  echo "note: building for one architecture only (add the other with: rustup target add $X86 $ARM)" >&2
fi

PAYLOAD="$WORK/payload/usr/local/libexec/vazie-vpn"
mkdir -p "$PAYLOAD"
if [ ${#BINARIES[@]} -eq 2 ]; then
  lipo -create "${BINARIES[@]}" -output "$PAYLOAD/vazie-vpn-host"
else
  cp "${BINARIES[0]}" "$PAYLOAD/vazie-vpn-host"
fi
chmod 755 "$PAYLOAD/vazie-vpn-host"
# Extended attributes would travel as `._*` files inside the package.
xattr -cr "$WORK/payload" 2>/dev/null || true

if [ -n "${APP_SIGN_IDENTITY:-}" ]; then
  codesign --force --options runtime --timestamp --sign "$APP_SIGN_IDENTITY" "$PAYLOAD/vazie-vpn-host"
fi

mkdir -p "$OUT"
UNSIGNED="$WORK/VazieVPNHelper.pkg"
pkgbuild \
  --root "$WORK/payload" \
  --identifier app.vazie.vpn.helper \
  --version "$VERSION" \
  --install-location / \
  --ownership recommended \
  --filter '/\._[^/]*$' \
  --scripts "$HERE/scripts" \
  "$UNSIGNED"

if [ -n "${PKG_SIGN_IDENTITY:-}" ]; then
  productsign --sign "$PKG_SIGN_IDENTITY" "$UNSIGNED" "$OUT/VazieVPNHelper.pkg"
else
  cp "$UNSIGNED" "$OUT/VazieVPNHelper.pkg"
  echo "note: the package is not signed; macOS will ask the person to right-click → Open" >&2
fi

if [ -n "${NOTARY_PROFILE:-}" ] && [ -n "${PKG_SIGN_IDENTITY:-}" ]; then
  xcrun notarytool submit "$OUT/VazieVPNHelper.pkg" --keychain-profile "$NOTARY_PROFILE" --wait
  xcrun stapler staple "$OUT/VazieVPNHelper.pkg"
fi

( cd "$OUT" && shasum -a 256 VazieVPNHelper.pkg > VazieVPNHelper.pkg.sha256 )
echo "$OUT/VazieVPNHelper.pkg"
