# GoDark Java Examples (Darkpool MM distribution)

This repository is a market-maker-facing distribution for GoDark's Java SDK.
It includes:

- a vendored **`godark-*-all.jar`** uber-JAR plus the upstream commit pin under
  `sdk/UPSTREAM_REF` — **no private Maven registry required**, the same idea
  as shipping the **`godark` wheel** in the Python MM bundle or **`libgodark.a`**
  in the C++ MM bundle
- minimal darkpool trading examples (**market** and **limit** orders only in
  the samples)
- a simple **`.env`** workflow (no shell `export` required)

The JAR is a *shaded* build: every transitive dependency
(`io.netty.*`, `com.fasterxml.jackson.*`, `com.google.*`, …) is rolled in,
so no third-party Maven registry lookup is needed at consumer-side build
time — only the **`godark`** package itself comes entirely from this repo.

## Prerequisites

| Item | Requirement |
|------|-------------|
| JDK | **17+** (Temurin or your distro's OpenJDK) |
| OS | Linux x86_64 recommended (matches published release ZIPs) |

Example on Debian/Ubuntu — install JDK 17 once:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk zip unzip
```

You need a vendored uber-JAR at `sdk/lib/godark-*-all.jar`. If it is missing
after a fresh clone, run `bash scripts/refresh_sdk.sh /path/to/gdx-java-sdk`
once (see [Refreshing `sdk/`](#refreshing-sdk-internal)). Release ZIPs already
include `sdk/lib/`.

## Testnet onboarding

Before running the examples, complete this setup flow:

1. Open the testnet frontend: `https://app.godark-dex.com`
2. Create an account using email sign-up.
3. Fund your testnet account using the faucet: `https://faucet.godark-dex.com`
4. In the frontend, go to **Settings → API Key Management** and click **Create API Key**.
5. Use the generated key ID and secret for your local `.env`.

## Configure credentials

Copy the template and fill in your API credentials:

```bash
cp examples/.env.example examples/.env
```

Required:

- `GODARK_API_KEY_ID`
- `GODARK_API_SECRET`
- `GODARK_PASSPHRASE` — required for API key-pair auth.

Optional:

- `GODARK_EDGE_URL` — override the edge URL (default: public testnet `wss://api.godark-dex.com` via the SDK Testnet environment preset).
- `GDX_NOISE_STATIC_PUBLIC_KEY` — override the sequencer Noise pin. **Not required for public testnet** — the SDK Environment Testnet preset bakes it in. Aliases: `GDX_NOISE_STATIC_PUBKEY`, `GODARK_NOISE_STATIC_PUBLIC_KEY`.
- `GODARK_USER_UUID` — some local edges need an explicit UUID from auth.
- `GODARK_TLS_SKIP_VERIFY` — set to `1` / `true` for dev TLS on `wss://`.

Legacy `GDX_*` names are accepted when the matching `GODARK_*` key is unset.

## Install

### From a released ZIP (recommended for MMs)

Download the latest **`gdx-java-sdk-*.zip`** from
[GitHub Releases](https://github.com/gq-godark/gdx-java-sdk-examples/releases)
and unzip it. The bundle contains the prebuilt `godark-*-all.jar`, the Gradle
example project, and `.env.example` at the bundle root.

```bash
unzip gdx-java-sdk-*.zip
cd gdx-java-sdk-*/
cp .env.example .env
# fill in GODARK_API_KEY_ID, GODARK_API_SECRET, GODARK_PASSPHRASE

cd examples
chmod +x gradlew    # if needed
./gradlew --no-daemon runQuickstart
./gradlew --no-daemon runFullTraderExample
./gradlew --no-daemon runRestClientExample
```

The Gradle wrapper handles its own bootstrap (no system Gradle required) and
the bundled JAR is referenced from the project via `implementation(files(...))`,
so no Maven repository configuration is needed.

### From a git clone (development)

Credentials must live under `examples/` (Gradle’s working directory). The root
`.env.example` is only a pointer — do not copy it.

```bash
git clone https://github.com/gq-godark/gdx-java-sdk-examples.git
cd gdx-java-sdk-examples
cp examples/.env.example examples/.env
# fill in credentials

cd examples
./gradlew --no-daemon runQuickstart
./gradlew --no-daemon runFullTraderExample
./gradlew --no-daemon runRestClientExample
```

List available tasks:

```bash
./gradlew tasks --group=examples
```

## Examples

| Sample | Gradle task | Purpose |
|--------|-------------|---------|
| `Quickstart.java` | `./gradlew runQuickstart` | Minimal connect → `subscribe("orders")` → LIMIT sell far from touch → cancel (book confirmation needs the private orders channel) |
| `FullTraderExample.java` | `./gradlew runFullTraderExample` | Reference flow: callbacks, place / modify / cancel, mass-quote / batch-cancel, session summary |
| `RestClientExample.java` | `./gradlew runRestClientExample` | Residual HTTP: public GETs, auth, me / leverage / balance, Noise XK note |

Order-type support in this MM distribution is limited to **`MARKET`** and
**`LIMIT`**.

## Packaging for market makers

Build a clean distributable ZIP from a sibling upstream checkout (the script
verifies the vendored JAR matches a fresh build at the recorded pin):

```bash
UPSTREAM_SRC=/path/to/gdx-java-sdk bash scripts/package.sh
```

The ZIP includes:

- `sdk/lib/godark-*-all.jar` — vendored uber-JAR (parity-checked against the
  pin at every release)
- `sdk/UPSTREAM_REF` — exact upstream `gdx-java-sdk` commit
- `sdk/shared/symbols.json` — symbol map snapshot
- `examples/` — Gradle project (`./gradlew runQuickstart`, …)
- `README.md`, `SDK_REFERENCE.md`, `.env.example`

Internal-only paths (`scripts/refresh_sdk.sh`, `scripts/package.sh`, `.git/`,
local `.env`, build artifacts) are **not** included.

CI publishes a tagged `gdx-java-sdk-*.zip` on every push to `main` via
`.github/workflows/release.yml`; download from
[GitHub Releases](https://github.com/gq-godark/gdx-java-sdk-examples/releases).

## Layout

| Path | Purpose |
|------|---------|
| `examples/` | Gradle project (`./gradlew runQuickstart`, …) |
| `sdk/lib/` | Vendored `godark-*-all.jar` |
| `sdk/UPSTREAM_REF` | Pinned `gdx-java-sdk` git commit for the JAR |
| `sdk/shared/symbols.json` | Symbol map snapshot |
| `bundle/README.md` | Recipient-facing README packaged into the release ZIP |
| `bundle/SDK_REFERENCE.md` | Recipient-facing API reference packaged into the release ZIP |
| `SDK_REFERENCE.md` | Maintainer-grade API reference; mirrored in trimmed form at `bundle/SDK_REFERENCE.md` |
| `scripts/refresh_sdk.sh` | Rebuild `sdk/lib/` from a local `gdx-java-sdk` checkout + bump pin |
| `scripts/package.sh` | Assemble the release ZIP (parity-checked against the pin) |
| `.github/workflows/release.yml` | CI + GitHub Release on `main` |
| `.github/workflows/auto-bump-sdk-pin.yml` | Layer 2 listener that auto-PRs vendored-JAR refreshes when upstream `gdx-java-sdk` ships |

## Refreshing `sdk/` (internal)

From a clean `gdx-java-sdk` checkout at the commit you want to ship:

```bash
bash scripts/refresh_sdk.sh /path/to/gdx-java-sdk
git add sdk/
git commit -m "refresh: sync vendored jar with upstream"
```

The Layer 2 listener (`auto-bump-sdk-pin.yml`) wraps this loop into a rolling
auto-PR triggered by `gdx-java-sdk` pushes to `main`.
