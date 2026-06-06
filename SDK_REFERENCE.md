# GoDark Java SDK Reference (developer / maintainer)

This is the comprehensive reference for maintainers and developers working
*inside* this repository (writing or modifying examples, reviewing the
vendored JAR, refreshing pins, etc.).

A trimmed, recipient-facing copy is maintained at
[`bundle/SDK_REFERENCE.md`](bundle/SDK_REFERENCE.md) and is the one copied
into the root of released ZIP bundles as `SDK_REFERENCE.md`. The bundle
version intentionally omits sections that recipients don't need (the
standalone-bot walkthrough, JAR layout / internals, refresh discipline, and
sourcing-from-git instructions).

> Scope: the MM examples use **WebSocket encrypted trading** via
> `godark.GodarkClient`. REST and standalone market-data clients ship in
> the same JAR but are outside the bundled examples in this distribution.
> Order placement support is limited to `MARKET` and `LIMIT`.

## Quick Start

```java
import godark.GodarkClient;
import godark.GodarkException;
import godark.Types;

public class Bot {
  public static void main(String[] args) throws GodarkException {
    try (GodarkClient client =
        GodarkClient.builder()
            .baseUrl("wss://api.godark-dex.com")
            .apiKeyId("gdk_...")
            .apiSecret("...")
            .passphrase("...")
            .build()) {
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

## Configuration

The **bundled Gradle examples** read credentials from `.env` / `.env.example`
(merged by the examples harness under `exchange.godark.examples.support.Dotenv`);
they do **not** read `System.getenv` for those keys.

For your **own JVM process** (a bot, a service), you normally pass credentials
from `System.getenv`, flags, or your config layer. Alternatively, if you omit
`apiKeyId` / `apiSecret` / `apiKey` on `GodarkClient.Builder`, the SDK uses
`godark.EnvFiles`: it reads the **process environment first**, then a single
`.env` file in the JVM **working directory** (`user.dir`) — not the same
multi-path merge as the bundled examples' `Dotenv`.

Typical variables:

- `GODARK_API_KEY_ID` (required for id/secret auth)
- `GODARK_API_SECRET` (required)
- `GODARK_PASSPHRASE` (required for API key-pair auth)
- `GODARK_EDGE_URL` (optional host origin; client appends `/ws/v1`)

Use `.env.example` as the template when using the file-based examples layout.

## Adding `godark` to your project

In this repository, the example Gradle module depends on the vendored uber-JAR
under `sdk/lib/`:

```kotlin
dependencies {
  implementation(files("../sdk/lib/godark-0.1.0-all.jar"))
}
```

To consume the JAR from your own project outside this repo, either:

1. Copy `sdk/lib/godark-*-all.jar` into your project and point
   `implementation(files(...))` at it (the same way this repo does), or
2. Build it from the upstream source pinned in
   [`sdk/UPSTREAM_REF`](sdk/UPSTREAM_REF):

   ```bash
   git clone https://github.com/gq-godark/gdx-java-sdk.git
   cd gdx-java-sdk
   git checkout <sha from sdk/UPSTREAM_REF>
   ./gradlew --no-daemon shadowJar
   # output: build/libs/godark-<version>-all.jar
   ```

Both paths use a single uber-JAR with all transitive dependencies shaded in;
no private Maven registry or repository configuration is required at the
consumer site.

## GodarkClient API

**Package:** `godark` (`GodarkClient` is a concrete class; construct with
`GodarkClient.builder()` or legacy `new GodarkClient(apiKey)` for opaque keys.)

### Core lifecycle

| Method | Signature | Purpose |
|--------|-----------|---------|
| `connect` | `void connect() throws GodarkException` | Authenticate and establish encrypted session |
| `disconnect` | `void disconnect()` | Close socket and reset session |
| `logout` | `void logout() throws GodarkException` | Logout then disconnect |
| `close` | `void close()` | `AutoCloseable` — delegates to `disconnect()` |
| `userUuid` | `Optional<String> userUuid()` | Authenticated user id after connect |

### Trading commands

| Method | Signature | Purpose |
|--------|-----------|---------|
| `placeOrder` | `OrderAck placeOrder(String symbol, String side, String orderType, double quantity, Double price, String timeInForce, boolean aon, Double minFillSize, Long expiryTime) throws GodarkException` | Place encrypted order |
| `cancelOrder` | `OrderAck cancelOrder(String orderId, String symbol) throws GodarkException` | Cancel by id (overload defaults symbol to `BTC-USDC-PERP`) |
| `modifyOrder` | `OrderAck modifyOrder(String orderId, String symbol, Double newPrice, Double newQuantity) throws GodarkException` | Modify price and/or quantity |

`side`, `orderType`, and `timeInForce` are **strings** at the command boundary
(for example `"SELL"`, `"LIMIT"`, `"GTC"`). Stream updates use protobuf enums on
the wire (see **Enums**).

### Streams

| Method | Signature | Purpose |
|--------|-----------|---------|
| `subscribe` | `void subscribe(String... channels) throws GodarkException` | Subscribe to private channels (`orders`, `positions`) |
| `subscribe` | `void subscribe() throws GodarkException` | Subscribe to `orders` and `positions` |
| `unsubscribe` | `void unsubscribe(String... channels) throws GodarkException` | Unsubscribe |
| `pollOrderUpdate` | `Optional<OrderUpdate> pollOrderUpdate(long millis) throws InterruptedException` | Blocking poll from order queue |
| `pollPositionUpdate` | `Optional<PositionUpdate> pollPositionUpdate(long millis) throws InterruptedException` | Blocking poll from position queue |

Additional `poll*` methods exist for every other sequencer push type (see
**Callbacks**).

### Callbacks

```java
client.onOrderUpdate(u -> { });
client.onPositionUpdate(u -> { });
client.onReconnect(() -> { });
client.onError(e -> { });
```

Each `on*` registrar appends a listener; multiple subscribers are allowed.

Sequencer pushes beyond orders and positions use the same pattern — each has an
`on*` registrar **and** a matching `poll*` method on a bounded queue:

```java
client.onPositionsSnapshot(s -> { });
client.onSystemHealth(h -> { });
client.onBalanceUpdate(b -> { });
client.onMarginAlert(a -> { });
client.onFundingRateUpdate(f -> { });
client.onSettlementUpdate(s -> { });
```

| Push | Field highlights | Typical use |
|------|------------------|-------------|
| `PositionsSnapshot` | `rows()` (`PositionRow` with `symbolId`, `side`, `size`, `entryPrice`, `markPrice`, …), `source`, `serverTimestamp` | Hydrate open positions on connect; periodic refresh |
| `SystemHealthUpdate` | `totalNodes`, `ready`, `degraded`, `acceptingOrders` | Cluster status; pause submissions if not accepting |
| `BalanceUpdate` | `shieldedBalanceRaw` | Wallet / equity after fills or settlement |
| `MarginAlert` | `symbolId`, `tier`, `marginRatioBps`, `liquidationPriceBps`, `recovered` | Margin banner per owner and symbol |
| `FundingRateUpdate` | `symbolId`, `currentRate`, `predictedRate`, `nextFundingTime` | Funding ticker / metadata |
| `SettlementUpdate` | `batchId`, `status`, `txSignature`, `affectedUserUuids` | Batch reconciliation |

Each stream uses a single bounded queue per type (default capacity from
`GodarkClient.Builder.streamBufferSize`, typically **256**). When a queue is
full, the oldest entry may be dropped so the client stays live; both the
matching callback and `poll*` observe the same items.

### Concurrency rule

Only one encrypted command (`placeOrder`, `cancelOrder`, `modifyOrder`) should
be in flight at a time. Complete each call (or handle its exception) before
issuing the next. Push streams above may be consumed concurrently from
independent threads — that's the intended pattern in `FullTraderExample`.

## Core Types

**Package:** `godark` — value records in `godark.Types`.

Wire decimals are often exposed as **strings** on push types to preserve
sequencer precision. Command APIs use `double` / `Double` where noted on
`placeOrder`.

### OrderAck

- `orderId` (`String`)
- `success` (`boolean`)
- `sequence` (`String`)
- `errorCode` (`String`, nullable) — symbolic code such as
  `"PRICE_DEVIATION_TOO_LARGE"` or `"MARGIN_INSUFFICIENT"`
- `error` (`String`, nullable) — human-readable message

### OrderUpdate

Record fields include `orderId`, `symbolId`, `side`, `status`, `updateType`,
`price`, `quantity`, `filledQty`, `remainingQty`, `cumFill`, `cancelReason`,
`rejectReason`, `correlationId`, `timestamp`. Stringly-typed decimals
preserve precision; status/lifecycle fields use the protobuf enums under
`gdx.common.v1.Types`.

### PositionUpdate

Per-fill delta. Use this stream to drive incremental P&L / position accounting
between `PositionsSnapshot` refreshes.

Record fields include `userUuid`, `symbolId`, `side`, `updateType`, `size`,
`entryPrice`, `previousSize`, `fillPrice`, `fillQty`, `correlationId`,
`timestamp`.

### PositionRow / PositionsSnapshot

`PositionsSnapshot` is the periodic / event-triggered authoritative view of
all open positions for the authenticated user. `rows()` holds one
`PositionRow` per `(symbolId, side)` pair, each with `size`, `entryPrice`,
`leverage`, and (when fresh) `markPrice` / `unrealizedPnl` / `notional`.
The snapshot also carries a `source` (`INITIAL` / `PERIODIC` / `EVENT`) and
the `serverTimestamp`.

### Other push payloads

| Type | Notable accessors |
|------|-------------------|
| `Types.SystemHealthUpdate` | `totalNodes`, `acceptingOrders`, `ready`, `degraded`, `exhausted`, `warming`, `draining`, `waiting` |
| `Types.BalanceUpdate` | `userUuid`, `shieldedBalanceRaw`, `timestamp` |
| `Types.MarginAlert` | `owner`, `symbolId`, `tier`, `marginRatioBps`, `markPriceBps`, `liquidationPriceBps`, `stateVersion`, `recovered`, `ts` |
| `Types.FundingRateUpdate` | `symbolId`, `currentRate`, `predictedRate`, `nextFundingTime`, `timestamp` |
| `Types.SettlementUpdate` | `batchId`, `status` (`SettlementBatchStatus`), `txSignature`, `timestamp`, `affectedUserUuids` |

## Enums

Protobuf enums used on streams and acks live under **`gdx.common.v1.Types`**
inside the JAR (for example `Side`, `OrderStatus`, `OrderUpdateType`,
`PositionUpdateType`, `CancelReason`, `PositionsSnapshotSource`,
`SettlementBatchStatus`). Command parameters still use **string** labels as
in **Trading commands**.

Commonly used wire values include:

- **Side:** `BUY`, `SELL`
- **Order types (wire / compatibility):** includes `MARKET`, `LIMIT`, and
  additional pegged types the sequencer understands
- **Time in force:** `GTC`, `IOC`, `FOK`, `GTD`
- **Order status / update types:** `NEW`, `FILLED`, `CANCELLED`, `REJECTED`,
  `MODIFIED`, … (see generated enum definitions in the JAR)

Note: the wire enum includes additional order types for compatibility, but
this MM distribution supports placing only **`MARKET`** and **`LIMIT`**
orders.

## Errors

### GodarkException variants

Checked failures extend **`godark.GodarkException`** (and runtime problems may
still surface through `onError`):

- `AuthenticationException` — auth or handshake failure
- `SessionException` — ECDH / session setup or encryption session errors
- `OrderRejectedException` — order rejected by the edge; use `errorCode()` for
  symbolic reasons
- `ConnectionException` — transport-level disconnect or failure
- `EncryptionException` — payload crypto errors
- `CommandTimeoutException` — command or auth exceeded transport timeouts

The `OrderRejectedException` variant is the one application code typically
branches on:

```java
try {
  Types.OrderAck ack = client.placeOrder(...);
  if (!ack.success()) {
    System.err.println("rejected: " + ack.error() + " (code=" + ack.errorCode() + ")");
  }
} catch (godark.OrderRejectedException ore) {
  System.err.println("rejected: " + ore.getMessage() + " (code=" + ore.errorCode() + ")");
}
```

### Order error codes

The sequencer's numeric ack codes are mapped to symbolic strings (e.g.
`PRICE_DEVIATION_TOO_LARGE`, `MARGIN_INSUFFICIENT`,
`SELF_TRADE_PREVENTION`) by the **`godark.OrderErrors`** lookup table:

| Symbol | Purpose |
|--------|---------|
| `OrderErrors.find(int code)` | Numeric ack code → `OrderErrors.Entry` |
| `OrderErrors.findSymbolic(String symbolic)` | Reverse lookup by symbolic name |
| `OrderErrors.Entry` | `{ code, symbolic, description }` |

The `OrderAck.errorCode()` and `OrderRejectedException.errorCode()` accessors
already return the symbolic string, so most callers won't need the lookup
directly — it's primarily there for debugging and for renderers that want to
surface the long-form description alongside the symbolic name.

See the bundled `Quickstart.java` / `FullTraderExample.java` sources for the
end-to-end try/catch / `onError` pattern.

## Example files in this distribution

| File | Gradle task | Purpose |
|------|-------------|---------|
| `examples/src/main/java/exchange/godark/examples/Quickstart.java` | `./gradlew runQuickstart` | Minimal connect, place, cancel |
| `examples/src/main/java/exchange/godark/examples/FullTraderExample.java` | `./gradlew runFullTraderExample` | Reference flow with callbacks and order lifecycle |
| `examples/src/main/java/exchange/godark/examples/support/Dotenv.java` | (helper) | Multi-path `.env` loader used by both example mains |

## Gradle integration (your own bot)

Add the vendored uber-JAR to your Gradle module (path is relative to that
module's `build.gradle.kts`; adjust if the JAR lives elsewhere):

```kotlin
dependencies {
  implementation(files("sdk/lib/godark-0.1.0-all.jar"))
}
```

Match the filename under `sdk/lib/` to the version shipped in this bundle (see
`sdk/UPSTREAM_REF` and the JAR name on disk).

## Standalone bot (`java` / `javac`)

Compile and run without Gradle: put `godark-*-all.jar` on the classpath together
with your compiled classes.

```bash
javac --release 17 -cp 'sdk/lib/*' -d out src/MyBot.java
GODARK_API_KEY_ID=... GODARK_API_SECRET=... GODARK_PASSPHRASE=... java -cp "out:sdk/lib/*" com.example.MyBot
```

Minimal bot (same flow as the bundled quickstart: connect, far limit sell,
cancel). **Verified** against the public `GodarkClient` API and the vendored
JAR in this distribution:

```java
import godark.GodarkClient;
import godark.GodarkException;
import godark.Types;
import java.util.Optional;

public class MyBot {

  public static void main(String[] args) throws GodarkException {
    String kid = System.getenv("GODARK_API_KEY_ID");
    String sec = System.getenv("GODARK_API_SECRET");
    String pass = System.getenv("GODARK_PASSPHRASE");
    if (kid == null
        || kid.isBlank()
        || sec == null
        || sec.isBlank()
        || pass == null
        || pass.isBlank()) {
      System.err.println("Set GODARK_API_KEY_ID, GODARK_API_SECRET and GODARK_PASSPHRASE");
      System.exit(1);
      return;
    }
    String base =
        Optional.ofNullable(System.getenv("GODARK_EDGE_URL"))
            .filter(s -> !s.isBlank())
            .orElse("wss://api.godark-dex.com");

    try (GodarkClient client =
        GodarkClient.builder().baseUrl(base).apiKeyId(kid).apiSecret(sec).passphrase(pass).build()) {
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

Use `try-with-resources` so `close()` / `disconnect()` runs even if `connect()`
or trading throws. Handle `GodarkException` in production (log, backoff, or
surface `OrderRejectedException#errorCode()`).

## Vendored layout (`sdk/`)

The `godark` SDK is vendored as a fat JAR under `sdk/`:

```text
sdk/
├── UPSTREAM_REF              # exact upstream commit SHA the JAR was built from
├── lib/
│   └── godark-0.1.0-all.jar  # shaded uber-JAR (no private Maven registry needed)
└── shared/
    └── symbols.json          # canonical perp symbol table snapshot
```

The uber-JAR contains both the **public `godark.*` API** (`GodarkClient`,
`Types`, `Enums`, `*Exception`, `OrderErrors`, …) and the protobuf-generated
`gdx.common.v1.*`, `gdx.edge.v1.*`, `gdx.sequencer.v1.*` types under their
own packages. Transitive runtime dependencies (`io.netty.*`,
`com.fasterxml.jackson.*`, `com.google.*`, …) are shaded in; the JAR is
self-contained.

## Refreshing the vendored JAR

Maintainers refresh `sdk/` from a sibling `gdx-java-sdk` checkout:

```bash
./scripts/refresh_sdk.sh /path/to/gdx-java-sdk
```

The script:

1. Refuses to run if the upstream worktree is dirty (so the recorded SHA
   matches reality).
2. Runs `./gradlew --no-daemon shadowJar` in the upstream checkout to
   rebuild the shadow JAR.
3. Copies the produced `build/libs/godark-*-all.jar` into `sdk/lib/`,
   replacing any older variant.
4. Refreshes `sdk/shared/symbols.json` from upstream.
5. Writes the upstream HEAD SHA into `sdk/UPSTREAM_REF`.

After running it, `scripts/package.sh` performs a parity check — it rebuilds
the JAR a second time at the pinned SHA and confirms the vendored copy has
the same content as the freshly-built one. Layer 2 automation
(`auto-bump-sdk-pin.yml`) wraps this loop into a rolling auto-PR triggered by
SDK pushes.
