#!/usr/bin/env bash
# Refresh sdk/lib/*.jar + sdk/shared/symbols.json from a local gdx-java-sdk checkout and record the
# upstream commit in sdk/UPSTREAM_REF.
#
# Usage:
#   ./scripts/refresh_sdk.sh /path/to/gdx-java-sdk
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/gdx-java-sdk" >&2
  exit 1
fi

SRC="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST_SDK="$REPO_ROOT/sdk"

if [[ ! -d "$SRC" ]]; then
  echo "error: source directory '$SRC' does not exist" >&2
  exit 1
fi

if [[ ! -d "$SRC/.git" ]]; then
  echo "error: '$SRC' is not a git checkout — pin cannot be recorded" >&2
  exit 1
fi

if [[ ! -f "$SRC/gradlew" ]]; then
  echo "error: '$SRC/gradlew' missing — not an SDK root?" >&2
  exit 1
fi

if ! git -C "$SRC" diff --quiet || ! git -C "$SRC" diff --cached --quiet; then
  echo "error: upstream '$SRC' has uncommitted changes; commit or stash first" >&2
  exit 1
fi

UPSTREAM_SHA="$(git -C "$SRC" rev-parse HEAD)"
UPSTREAM_TAG="$(git -C "$SRC" describe --tags --exact-match HEAD 2>/dev/null || true)"

echo "Refreshing $DEST_SDK from $SRC ..."
echo "  upstream HEAD: $UPSTREAM_SHA${UPSTREAM_TAG:+ (tag $UPSTREAM_TAG)}"

(
  cd "$SRC"
  git submodule update --init --recursive --depth 1
  ./gradlew --no-daemon shadowJar
)

VERSION="$(grep '^version=' "$SRC/gradle.properties" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
JAR="$SRC/build/libs/godark-${VERSION}-all.jar"
if [[ ! -f "$JAR" ]]; then
  echo "error: expected jar missing: $JAR" >&2
  exit 1
fi

mkdir -p "$DEST_SDK/lib" "$DEST_SDK/shared"
rm -f "$DEST_SDK/lib"/godark-*-all.jar
cp "$JAR" "$DEST_SDK/lib/"
cp "$SRC/shared/symbols.json" "$DEST_SDK/shared/symbols.json"

if [[ -n "$UPSTREAM_TAG" ]]; then
  echo "$UPSTREAM_TAG" >"$DEST_SDK/UPSTREAM_REF"
else
  echo "$UPSTREAM_SHA" >"$DEST_SDK/UPSTREAM_REF"
fi

echo "  wrote pin: $(tr -d '[:space:]' <"$DEST_SDK/UPSTREAM_REF") -> sdk/UPSTREAM_REF"
echo
echo "Next steps:"
echo "  git add sdk/ UPSTREAM_REF && git commit -m 'refresh: sync vendored jar with upstream $(tr -d '[:space:]' <"$DEST_SDK/UPSTREAM_REF")'"
