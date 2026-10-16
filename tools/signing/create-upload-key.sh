#!/usr/bin/env bash

set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
KEYSTORE_DIR="${VAZIE_KEYSTORE_DIR:-$REPOSITORY_ROOT/signKeystore}"
KEYSTORE="$KEYSTORE_DIR/keypass.jks"
KEY_PROPERTIES="$REPOSITORY_ROOT/signKeystore/key.properties"
ALIAS="${VAZIE_KEY_ALIAS:-vazie-vpn-upload}"


KEY_SIZE=4096
VALIDITY_DAYS=10000

info() { printf '\033[1;34m==\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m!!\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31mXX\033[0m %s\n' "$*" >&2; exit 1; }

command -v keytool >/dev/null 2>&1 || die "keytool is not on PATH; install a JDK first"

if [ -e "$KEYSTORE" ]; then
    die "$KEYSTORE already exists.
   Refusing to touch it. Overwriting a release keystore is the one mistake in this file that
   cannot be undone, so this script will not do it even with a flag. If you are certain the
   existing file is not the key that signed anything published, move it aside by hand first."
fi

mkdir -p "$KEYSTORE_DIR" "$(dirname "$KEY_PROPERTIES")"
chmod 700 "$KEYSTORE_DIR" "$(dirname "$KEY_PROPERTIES")"

cat <<'NOTE'

  The certificate's distinguished name is public: it is embedded in every APK you sign and anyone
  who installs one can read it. Put something real and stable in it — it identifies the publisher,
  and it cannot be changed later without changing the key.

NOTE

read -r -p "  Common name (e.g. your name or the studio's) : " CN
read -r -p "  Organisation (blank is fine for one person) : " O
read -r -p "  Two-letter country code (e.g. RU, NL, DE)   : " C

[ -n "$CN" ] || die "the common name cannot be empty"
case "$C" in
    [A-Za-z][A-Za-z]) : ;;
    *) die "the country must be a two-letter ISO code, got: '$C'" ;;
esac

DNAME="CN=$CN, O=${O:-$CN}, C=$(printf '%s' "$C" | tr '[:lower:]' '[:upper:]')"

cat <<NOTE

  Creating: $KEYSTORE
  Alias:    $ALIAS
  Subject:  $DNAME
  Key:      RSA $KEY_SIZE, valid $VALIDITY_DAYS days

  keytool will now ask for a password. It is asked twice and echoed nowhere.

  The keystore is PKCS12, so the key password and the store password are the same value — press
  RETURN when keytool offers to reuse it. Both VAZIE_KEYSTORE_PASSWORD and VAZIE_KEY_PASSWORD in
  CI therefore get that one password.

NOTE

keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize "$KEY_SIZE" \
    -validity "$VALIDITY_DAYS" \
    -dname "$DNAME"

chmod 600 "$KEYSTORE"

info "created $KEYSTORE"

if [ "$KEYSTORE_DIR" = "$REPOSITORY_ROOT/signKeystore" ]; then
    STORE_FILE="$(basename "$KEYSTORE")"
else
    STORE_FILE="$KEYSTORE"
fi

if [ -e "$KEY_PROPERTIES" ]; then
    chmod 600 "$KEY_PROPERTIES"
    warn "$KEY_PROPERTIES already exists; leaving it alone"
else
    cat > "$KEY_PROPERTIES" <<PROPERTIES
# Local signing material for release builds. Never committed: .gitignore excludes the whole signKeystore/
# directory, and separately excludes key.properties and *.jks by name.

RELEASE_STORE_FILE=$STORE_FILE
RELEASE_STORE_PASS=
RELEASE_ALIAS=$ALIAS
RELEASE_KEY_PASS=
PROPERTIES
    chmod 600 "$KEY_PROPERTIES"
    info "wrote $KEY_PROPERTIES — fill in RELEASE_STORE_PASS and RELEASE_KEY_PASS by hand"
fi
echo
info "fingerprints — write these down; they are how you prove which key signed an artifact"
warn "keytool asks for the password once more here, to read the key back. This is not an error."
echo
# stderr is deliberately not suppressed: that is where keytool's prompt goes, and a hidden prompt
# looks like a hung script.
if ! keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" | grep -E "SHA1:|SHA256:|Valid from"; then
    warn "could not read the fingerprints back; run this yourself when you have a moment:"
    warn "  keytool -list -v -keystore \"$KEYSTORE\" -alias \"$ALIAS\""
fi

cat <<NOTE

  ---------------------------------------------------------------------------------------------
  Next, in this order:

  1. BACK IT UP, before you build anything with it.

         $KEYSTORE

     Two offline copies. Losing this file is unrecoverable for direct-download users.

  2. Record the fingerprints above and the password in whatever you use for credentials that must
     outlive a laptop.

  3. Put the password into the two empty lines of

         $KEY_PROPERTIES

     and that is the whole local setup. From then on:

         ./gradlew :app:assembleRelease

     No exports, no password in shell history, nothing to remember before a build. A source given
     some of its four values and not all of them fails rather than falling back to the debug key —
     see verifyReleaseSigning in app/build.gradle.kts.

  4. For CI, add four repository secrets. The keystore travels base64-encoded:

         ./tools/signing/keystore-to-ci-secret.sh "$KEYSTORE"

     gives you VAZIE_KEYSTORE_BASE64. The other three are VAZIE_KEYSTORE_PASSWORD,
     VAZIE_KEY_ALIAS and VAZIE_KEY_PASSWORD.

     Create the 'release' GitHub Environment with required reviewers and put them there, not in
     repository-wide secrets: that gate is what stops an accidental tag push from producing a
     signed artifact.
  ---------------------------------------------------------------------------------------------

NOTE
