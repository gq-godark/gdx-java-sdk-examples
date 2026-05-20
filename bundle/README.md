# GoDark Java SDK

This package provides the GoDark Java SDK (prebuilt **uber-JAR**
`godark-*-all.jar`) and minimal examples for encrypted darkpool trading.

Supported order types in this distribution: `MARKET`, `LIMIT`.

## Package contents

- `sdk/lib/` — prebuilt `godark-*-all.jar` for offline use (no private Maven registry required)
- `sdk/UPSTREAM_REF` — exact upstream git pin used to build the JAR
- `sdk/shared/symbols.json` — symbol map snapshot
- `examples/` — Gradle project (`./gradlew runQuickstart`, `./gradlew runFullTraderExample`)
- `SDK_REFERENCE.md` — API reference
- `.env.example` — environment template (copy to `.env` at the bundle root or under `examples/`)

## 1) Prerequisites

- Linux x86_64 (or macOS / Windows with JDK 17+)
- **JDK 17+** (Temurin recommended)

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk zip unzip
```

The Gradle wrapper (`./gradlew`) bootstraps its own Gradle distribution; you
do **not** need a system Gradle install.

## 2) Create testnet credentials

1. Open the testnet frontend: `https://app.godark-dex.com`
2. Create an account using email sign-up.
3. Fund your testnet account using the faucet: `https://faucet.godark-dex.com`
4. In the frontend, go to **Settings → API Key Management** and click **Create API Key**.
5. Use the generated key ID and secret for your local `.env`.

## 3) Configure environment

Copy `.env.example` to `.env` and set:

- `GODARK_API_KEY_ID`
- `GODARK_API_SECRET`

```bash
cp .env.example .env
# optional: cp .env.example examples/.env
```

Optional: `GODARK_EDGE_URL` (defaults to `wss://api.godark-dex.com`),
`GODARK_USER_UUID`, `GODARK_TLS_SKIP_VERIFY` for local edges.

## 4) Run quickstart

From the unzipped bundle root:

```bash
cd examples
chmod +x gradlew    # if needed (e.g. unzip from Windows lost the execute bit)
./gradlew --no-daemon runQuickstart
```

Or run the full trader example:

```bash
./gradlew --no-daemon runFullTraderExample
```

The vendored uber-JAR is referenced via `implementation(files(...))` in
`examples/build.gradle.kts`, so no Maven repository configuration is required.

## Gradle integration (your own bot)

Add the vendored uber-JAR to your Gradle module (replace the version with the
filename under `sdk/lib/`):

```kotlin
dependencies {
  implementation(files("sdk/lib/godark-0.1.0-all.jar"))
}
```

Then in `MyBot.java`:

```java
import godark.GodarkClient;
import godark.GodarkException;
import godark.Types;
import java.util.Optional;

public class MyBot {
  public static void main(String[] args) throws GodarkException {
    String kid = System.getenv("GODARK_API_KEY_ID");
    String sec = System.getenv("GODARK_API_SECRET");
    if (kid == null || sec == null) {
      System.err.println("Set GODARK_API_KEY_ID and GODARK_API_SECRET");
      System.exit(1);
    }
    String base =
        Optional.ofNullable(System.getenv("GODARK_EDGE_URL"))
            .filter(s -> !s.isBlank())
            .orElse("wss://api.godark-dex.com");

    try (GodarkClient client =
        GodarkClient.builder().baseUrl(base).apiKeyId(kid).apiSecret(sec).build()) {
      client.connect();
      Types.OrderAck ack =
          client.placeOrder(
              "BTC-USDC-PERP",
              "SELL",
              "LIMIT",
              0.01,
              999_999.0,
              "GTC",
              false,
              null,
              null);
      client.cancelOrder(ack.orderId(), "BTC-USDC-PERP");
    }
  }
}
```

See `SDK_REFERENCE.md` for the full client API.
