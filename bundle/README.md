# GoDark Java SDK

This package provides the GoDark Java SDK (prebuilt **uber-JAR** `godark-*-all.jar`) and minimal
examples for wire-level request construction. **v0.1** does not yet include the full encrypted
WebSocket client — use the Python SDK for production trading until Java reaches parity.

## Package contents

- `sdk/lib/` — prebuilt `godark-*-all.jar` (includes gRPC + Netty shaded + protobuf); no Maven
  registry required
- `sdk/UPSTREAM_REF` — exact `gdx-java-sdk` git commit this JAR was built from
- `sdk/shared/symbols.json` — cross-SDK symbol map snapshot
- `examples/` — Gradle project + sample `main` classes (`PlaceOrder`, `CancelOrder`, …)
- `SDK_REFERENCE.md` — API reference
- `.env.example` — environment template

## 1) Prerequisites

- Linux x86_64 (or macOS / Windows with JDK 17+)
- **JDK 17+** (Temurin recommended)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk zip unzip
```

## 2) Configure environment (optional)

For offline wire samples, defaults are sufficient. For live trading (once supported), copy and
fill:

```bash
cp .env.example .env
# export variables or use a tool that loads .env
```

## 3) Run an example

```bash
cd examples
chmod +x gradlew  # if needed
./gradlew --no-daemon runPlaceOrder
./gradlew --no-daemon runCancelOrder
./gradlew --no-daemon runStreamOrderbook
```

## 4) Use the JAR in your own Gradle project

```kotlin
dependencies {
  implementation(files("path/to/sdk/lib/godark-0.1.0-all.jar"))
}
```

Or plain `javac` / `java`:

```bash
javac -cp 'sdk/lib/*' -d out src/MyBot.java
java -cp "out:sdk/lib/*" com.example.MyBot
```
