# gdx-java-sdk-examples

Runnable **examples** and a **tagged ZIP release** (prebuilt `godark-*-all.jar` + samples) for the
[`gdx-java-sdk`](https://github.com/gq-godark/gdx-java-sdk) Java client.

This repository is a market-maker-facing distribution: **two** trading samples (the reference flow
uses **market** and **limit** order types), vendored SDK bits, and a simple **`.env`** workflow.
Gradle `run*` tasks execute the sample mains under `examples/src/main/java/`. Credentials and URL
overrides are read from **`support.Dotenv`** (merged `.env` / `.env.example` under the repo root
and under `examples/`).

## Prerequisites

| Item | Requirement |
|------|---------------|
| JDK | **17+** (Temurin or your distro’s OpenJDK) |
| OS | Linux x86_64 recommended (matches typical CI / release artifacts) |

You need a vendored uber-JAR at `sdk/lib/godark-*-all.jar`. If it is missing after a fresh clone, run `bash scripts/refresh_sdk.sh /path/to/gdx-java-sdk` once (see [Refresh vendored JAR](#refresh-vendored-jar-maintainers)). Release ZIPs already include `sdk/lib/`.

## Configure credentials

Copy the template and fill in API credentials:

```bash
cp .env.example .env
# or: cp examples/.env.example examples/.env
```

Required:

- `GODARK_API_KEY_ID`
- `GODARK_API_SECRET`

Optional:

- `GODARK_EDGE_URL` — if unset, examples default to `wss://api.godark-dex.com`.
- `GODARK_USER_UUID` — some local edges need an explicit UUID from auth.
- `GODARK_TLS_SKIP_VERIFY` — set to `1` / `true` for dev TLS on `wss://`.

Legacy `GDX_*` names are accepted when the matching `GODARK_*` key is unset.

## Run

From the Gradle project directory `examples/`:

```bash
cd examples
chmod +x gradlew   # Linux / macOS, if the execute bit was lost (e.g. unzip from Windows)
./gradlew --no-daemon runQuickstart
./gradlew --no-daemon runFullTraderExample
```

List tasks:

```bash
./gradlew tasks --group=examples
```

### Samples

| Sample | Gradle task |
|--------|-------------|
| Minimal limit sell far from touch, then cancel | `./gradlew runQuickstart` |
| Trader reference (callbacks, place / modify / cancel) | `./gradlew runFullTraderExample` |

## Repo layout

| Path | Purpose |
|------|---------|
| `examples/` | Gradle project (`./gradlew runQuickstart`, …) |
| `sdk/lib/` | Vendored `godark-*-all.jar` |
| `sdk/UPSTREAM_REF` | Pinned `gdx-java-sdk` git commit for the JAR |
| `sdk/shared/symbols.json` | Symbol map snapshot |
| `scripts/refresh_sdk.sh` | Rebuild `sdk/lib` from a local `gdx-java-sdk` checkout + bump pin |
| `scripts/package.sh` | Assemble the ZIP (parity-checked against the pin) |
| `.github/workflows/release.yml` | CI + GitHub Release on `main` |

## Refresh vendored JAR (maintainers)

From a clean `gdx-java-sdk` checkout at the commit you want to ship:

```bash
bash scripts/refresh_sdk.sh /path/to/gdx-java-sdk
git add sdk/
git commit -m "refresh: sync vendored jar with upstream"
```

## Local package smoke test

```bash
UPSTREAM_SRC=/path/to/gdx-java-sdk bash scripts/package.sh
```

## Releases

Download the latest **`gdx-java-sdk-examples-*.zip`** from **GitHub Releases**, unzip, and follow `README.md` inside the bundle.
