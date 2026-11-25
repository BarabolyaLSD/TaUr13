#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)"
VERSION="8.7"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
GH="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL="$GH/wrapper/dists/gradle-${VERSION}-bin/local-gradle-${VERSION}"
BIN="$INSTALL/gradle-${VERSION}/bin/gradle"
if [ -x "$BIN" ]; then exec "$BIN" "$@"; fi
mkdir -p "$INSTALL"
ARCHIVE="$INSTALL/gradle-${VERSION}-bin.zip"
URL="https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ARCHIVE"
elif command -v wget >/dev/null 2>&1; then wget -O "$ARCHIVE" "$URL"
else echo "Error: curl or wget is required to download Gradle ${VERSION}." >&2; exit 1; fi
if ! command -v unzip >/dev/null 2>&1; then echo "Error: unzip is required to unpack Gradle." >&2; exit 1; fi
unzip -q -o "$ARCHIVE" -d "$INSTALL"
exec "$BIN" "$@"
