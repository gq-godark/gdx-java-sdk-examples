#!/usr/bin/env bash
# MM bundle packager — JAR + examples zip, built strictly from the pinned upstream gdx-java-sdk
# commit in sdk/UPSTREAM_REF.
#
# Output layout:
#   <DIST_NAME>/
#   ├── .env.example
#   ├── README.md
#   ├── SDK_REFERENCE.md
#   ├── sdk/
#   │   ├── UPSTREAM_REF
#   │   ├── lib/godark-*-all.jar
#   │   └── shared/symbols.json
#   └── examples/   (Gradle runner + sample mains)
#
# Usage:
#   bash scripts/package.sh
#   bash scripts/package.sh my-release-name
#   UPSTREAM_SRC=/path/to/gdx-java-sdk bash scripts/package.sh
set -euo pipefail

UPSTREAM_REPO="gq-godark/gdx-java-sdk"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_NAME="${1:-gdx-java-sdk-examples-bundle}"

cd "$REPO_ROOT"

if [[ ! -f "${REPO_ROOT}/sdk/UPSTREAM_REF" ]]; then
  echo "error: sdk/UPSTREAM_REF missing — run scripts/refresh_sdk.sh first" >&2
  exit 1
fi
PINNED_REF="$(tr -d '[:space:]' <"${REPO_ROOT}/sdk/UPSTREAM_REF")"
if [[ -z "$PINNED_REF" ]]; then
  echo "error: sdk/UPSTREAM_REF is empty" >&2
  exit 1
fi

for required in bundle/README.md bundle/SDK_REFERENCE.md \
  examples/.env.example examples/build.gradle.kts examples/settings.gradle.kts examples/gradlew; do
  if [[ ! -f "${REPO_ROOT}/${required}" ]]; then
    echo "error: required source file missing: ${required}" >&2
    exit 1
  fi
done
if ! command -v zip >/dev/null 2>&1; then
  echo "error: 'zip' not found in PATH (apt-get install zip)" >&2
  exit 1
fi

CLEANUP_UPSTREAM=false

if [[ -n "${UPSTREAM_SRC:-}" ]]; then
  echo "Using UPSTREAM_SRC=${UPSTREAM_SRC}"
elif [[ -d "${REPO_ROOT}/../gdx-java-sdk/.git" ]]; then
  UPSTREAM_SRC="$(cd "${REPO_ROOT}/../gdx-java-sdk" && pwd)"
  echo "Using sibling upstream checkout: $UPSTREAM_SRC"
else
  CLEANUP_UPSTREAM=true
  UPSTREAM_SRC="$(mktemp -d)/gdx-java-sdk"
  echo "Cloning ${UPSTREAM_REPO}@${PINNED_REF} -> $UPSTREAM_SRC ..."
  if command -v gh >/dev/null 2>&1; then
    gh repo clone "${UPSTREAM_REPO}" "$UPSTREAM_SRC" -- --quiet --filter=blob:none
  else
    git clone --quiet --filter=blob:none "https://github.com/${UPSTREAM_REPO}.git" "$UPSTREAM_SRC"
  fi
  git -C "$UPSTREAM_SRC" checkout --quiet "$PINNED_REF"
fi

PARITY_TMP=""
cleanup() {
  if [[ -n "$PARITY_TMP" && -d "$PARITY_TMP" ]]; then
    rm -rf "$PARITY_TMP"
  fi
  if [[ "$CLEANUP_UPSTREAM" == true && -n "${UPSTREAM_SRC:-}" ]]; then
    rm -rf "$(dirname "$UPSTREAM_SRC")"
  fi
}
trap cleanup EXIT

if [[ ! -d "$UPSTREAM_SRC/.git" ]]; then
  echo "error: '$UPSTREAM_SRC' is not a git checkout — cannot verify pin" >&2
  exit 1
fi
upstream_head_sha="$(git -C "$UPSTREAM_SRC" rev-parse HEAD)"
upstream_pin_sha="$(git -C "$UPSTREAM_SRC" rev-parse "$PINNED_REF" 2>/dev/null || true)"
if [[ -z "$upstream_pin_sha" ]]; then
  echo "error: pinned ref '$PINNED_REF' does not resolve in $UPSTREAM_SRC" >&2
  echo "       (try: git -C $UPSTREAM_SRC fetch --tags origin)" >&2
  exit 1
fi
if [[ "$upstream_head_sha" != "$upstream_pin_sha" ]]; then
  echo "error: upstream HEAD ($upstream_head_sha) does not match pinned ref" >&2
  echo "       sdk/UPSTREAM_REF=$PINNED_REF -> $upstream_pin_sha" >&2
  echo "         git -C $UPSTREAM_SRC checkout $PINNED_REF" >&2
  exit 1
fi
echo "Upstream verified at pin: $PINNED_REF ($upstream_head_sha)"

(
  cd "$UPSTREAM_SRC"
  git submodule update --init --recursive --depth 1
  ./gradlew --no-daemon shadowJar
)

VERSION="$(grep '^version=' "$UPSTREAM_SRC/gradle.properties" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
BUILT_JAR="$UPSTREAM_SRC/build/libs/godark-${VERSION}-all.jar"
if [[ ! -f "$BUILT_JAR" ]]; then
  echo "error: upstream build produced no jar at $BUILT_JAR" >&2
  exit 1
fi

VENDOR_JAR="$(ls "${REPO_ROOT}/sdk/lib"/godark-*-all.jar 2>/dev/null | head -n1 || true)"
if [[ -z "$VENDOR_JAR" ]]; then
  echo "error: no vendored sdk/lib/godark-*-all.jar — run scripts/refresh_sdk.sh" >&2
  exit 1
fi

# Content-parity check: ensure every entry inside the vendored jar matches the
# corresponding entry inside the upstream shadowJar (same path, same bytes).
# We deliberately do NOT compare the whole-jar SHA256, because shadowJar's zip
# envelope (entry order, compression metadata, etc.) varies subtly across
# runner environments even with isPreserveFileTimestamps=false and
# isReproducibleFileOrder=true. What we actually care about is that no local
# edit to sdk/lib has introduced or modified a class file vs. the pinned
# upstream source.
PARITY_TMP="$(mktemp -d)"
unzip -q "$BUILT_JAR"  -d "$PARITY_TMP/built"
unzip -q "$VENDOR_JAR" -d "$PARITY_TMP/vendor"
if ! diff -rq "$PARITY_TMP/built" "$PARITY_TMP/vendor" >"$PARITY_TMP/diff.out" 2>&1; then
  echo "error: vendored jar content does not match upstream shadowJar for pin $PINNED_REF" >&2
  echo "  built:  $BUILT_JAR" >&2
  echo "  vendor: $VENDOR_JAR" >&2
  echo "::group::content diff (paths only)" >&2
  sed -E "s|$PARITY_TMP/built|<built>|g; s|$PARITY_TMP/vendor|<vendor>|g" \
    "$PARITY_TMP/diff.out" | head -50 >&2
  echo "::endgroup::" >&2
  echo "  fix: bash scripts/refresh_sdk.sh $UPSTREAM_SRC && git add sdk/ && git commit" >&2
  exit 1
fi
echo "Parity check passed: sdk/lib jar contents match upstream build (envelope may differ)"

STAGING_DIR="$(mktemp -d)"
DEST="$STAGING_DIR/$DIST_NAME"
mkdir -p "$DEST/sdk/lib" "$DEST/sdk/shared" "$DEST/examples"

echo "Staging distribution at $DEST ..."
cp "$BUILT_JAR" "$DEST/sdk/lib/"
cp "${REPO_ROOT}/sdk/UPSTREAM_REF" "$DEST/sdk/UPSTREAM_REF"
cp "${REPO_ROOT}/sdk/shared/symbols.json" "$DEST/sdk/shared/symbols.json"
mkdir -p "$DEST/examples"
cp -a "${REPO_ROOT}/examples/." "$DEST/examples/"
rm -rf "$DEST/examples/build" "$DEST/examples/.gradle" 2>/dev/null || true
cp "${REPO_ROOT}/examples/.env.example" "$DEST/.env.example"
cp "${REPO_ROOT}/bundle/README.md" "$DEST/README.md"
cp "${REPO_ROOT}/bundle/SDK_REFERENCE.md" "$DEST/SDK_REFERENCE.md"

ARCHIVE="$REPO_ROOT/${DIST_NAME}.zip"
rm -f "$ARCHIVE"
(
  cd "$STAGING_DIR"
  zip -qr "$ARCHIVE" "$DIST_NAME"
)
rm -rf "$STAGING_DIR"

echo
echo "Package created: $ARCHIVE"
LISTING="$(unzip -l "$ARCHIVE")"
echo "$LISTING"

if echo "$LISTING" | grep -E "${DIST_NAME}/scripts/" >/dev/null; then
  echo "error: bundle contains scripts/ — contract violated" >&2
  exit 1
fi
for required in \
  "${DIST_NAME}/sdk/lib/godark-.*\\.jar" \
  "${DIST_NAME}/examples/src/main/java/exchange/godark/examples/Quickstart\\.java" \
  "${DIST_NAME}/examples/src/main/java/exchange/godark/examples/FullTraderExample\\.java" \
  "${DIST_NAME}/README\\.md" \
  "${DIST_NAME}/SDK_REFERENCE\\.md" \
  "${DIST_NAME}/\\.env\\.example"; do
  if ! echo "$LISTING" | grep -E "${required}" >/dev/null; then
    echo "error: bundle missing required entry: ${required}" >&2
    exit 1
  fi
done

echo
echo "JAR + examples bundle assertion: PASSED"
echo "built from upstream: ${UPSTREAM_REPO}@${PINNED_REF} (${upstream_head_sha})"
