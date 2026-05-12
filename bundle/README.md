# GoDark Java SDK

This package provides the GoDark Java SDK (prebuilt **uber-JAR** `godark-*-all.jar`) and minimal
examples for encrypted darkpool trading.

Supported order types in this distribution: `MARKET`, `LIMIT`.

## Package contents

- `sdk/lib/` — prebuilt `godark-*-all.jar` for offline use (no private Maven registry required)
- `sdk/UPSTREAM_REF` — exact upstream git pin used to build the JAR
- `sdk/shared/symbols.json` — symbol map snapshot
- `examples/` — Gradle project (`./gradlew runQuickstart`, `./gradlew runFullTraderExample`)
- `SDK_REFERENCE.md` — short API orientation
- `.env.example` — environment template (copy to `.env` at package root or under `examples/`)

## 1) Prerequisites

- Linux x86_64 (or macOS / Windows with JDK 17+)
- **JDK 17+** (Temurin recommended)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk zip unzip
```

## 2) Create testnet credentials

1. Open frontend: `https://app.godark-dex.com`
2. Create an account using email.
3. Fund the account using faucet: `https://faucet.godark-dex.com`
4. Go to **Settings → API Key Management** and create an API key.

## 3) Configure environment

Copy `.env.example` to `.env` and set:

- `GODARK_API_KEY_ID`
- `GODARK_API_SECRET`

```bash
cp .env.example .env
# optional: cp .env.example examples/.env
```

Optional: `GODARK_EDGE_URL` (defaults to `wss://api.godark-dex.com`), `GODARK_USER_UUID`, `GODARK_TLS_SKIP_VERIFY` for local edges.

## 4) Run examples

From the unzipped root:

```bash
cd examples
chmod +x gradlew   # if needed
./gradlew --no-daemon runQuickstart
./gradlew --no-daemon runFullTraderExample
```

## 5) Use the JAR in your own project

Gradle (replace the version with the filename under `sdk/lib/`):

```kotlin
dependencies {
  implementation(files("sdk/lib/godark-0.1.0-all.jar"))
}
```

Or compile and run with `javac` / `java` (classes first, then JAR):

```bash
javac --release 17 -cp 'sdk/lib/*' -d out src/MyBot.java
java -cp "out:sdk/lib/*" com.example.MyBot
```

See `SDK_REFERENCE.md` (**Gradle integration** and **Standalone bot**) for a
minimal `MyBot` class (environment variables, `try-with-resources`, place /
cancel) verified against this JAR.
