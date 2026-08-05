# GoDark Java SDK Reference

This reference describes the API surface used by the bundled examples
shipped in this distribution. The examples use WebSocket encrypted trading
via `godark.GodarkClient`. Encrypted REST trading is not supported — all
order flow (place / modify / cancel / mass-quote) runs over the Noise XK
WebSocket client. A standalone market-data client also ships in the JAR but
is outside the bundled examples in this distribution.

Order placement support in this MM distribution is limited to `MARKET` and
`LIMIT`.

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

The **bundled Gradle examples** read credentials from the bundle-root
`.env` / `.env.example` (and optionally `examples/.env` to override), merged by
`exchange.godark.examples.support.Dotenv`; they do **not** read
`System.getenv` for those keys.

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
- `GDX_NOISE_STATIC_PUBLIC_KEY` (required for encrypted WebSocket trading) — 64 hex chars; aliases `GDX_NOISE_STATIC_PUBKEY`, `GODARK_NOISE_STATIC_PUBLIC_KEY`
- `GODARK_EDGE_URL` (optional host origin; client appends `/ws/v1`)

Use the bundle-root `.env.example` as the template (copy to `.env`, or to
`examples/.env` when running Gradle from `examples/`).

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
issuing the next.

## Core Types

**Package:** `godark` — value records in `godark.Types`.

Wire decimals are often exposed as **strings** on push types to preserve
sequencer precision. Command APIs use `double` / `Double` where noted on
`placeOrder`.

### OrderAck

- `orderId` (`String`)
- `success` (`boolean`)
- `sequence` (`String`)
- `errorCode` (`String`, nullable)
- `error` (`String`, nullable)

### OrderUpdate

Record fields include `orderId`, `symbolId`, `side`, `status`, `updateType`,
`price`, `quantity`, `filledQty`, `remainingQty`, `timestamp`, and related
lifecycle fields.

### PositionUpdate

Record fields include `userUuid`, `symbolId`, `side`, `updateType`, `size`,
`entryPrice`, `fillPrice`, `fillQty`, `timestamp`, and related lifecycle
fields.

## Enums

Protobuf enums used on streams and acks live under **`gdx.common.v1.Types`**
inside the JAR (for example `Side`, `OrderStatus`, `OrderUpdateType`,
`PositionUpdateType`, `CancelReason`). Command parameters still use **string**
labels as in **Trading commands**.

Commonly used wire values include:

- **Side:** `BUY`, `SELL`
- **Order types (wire / compatibility):** includes `MARKET`, `LIMIT`, and
  additional pegged types the sequencer understands
- **Time in force:** `GTC`, `IOC`, `FOK`, `GTD`
- **Order status / update types:** `NEW`, `FILLED`, `CANCELLED`, `REJECTED`,
  `MODIFIED`, … (see generated enum definitions in the JAR)

Note: the wire enum includes additional order types for compatibility, but this
MM distribution supports placing only **`MARKET`** and **`LIMIT`** orders.

## Errors

Checked failures extend **`godark.GodarkException`** (and runtime problems may
still surface through `onError`):

- `AuthenticationException` — auth or handshake failure
- `SessionException` — Noise XK handshake or encryption session errors
- `OrderRejectedException` — order rejected by the edge; use `errorCode()` for
  symbolic reasons (for example `PRICE_DEVIATION_TOO_LARGE`,
  `MARGIN_INSUFFICIENT`)
- `ConnectionException` — transport-level disconnect or failure
- `EncryptionException` — payload crypto errors
- `CommandTimeoutException` — command or auth exceeded transport timeouts

See the bundled `Quickstart` / `FullTraderExample` sources for try/catch
patterns.

## Example files in this distribution

| Gradle task | Purpose |
|-------------|---------|
| `./gradlew runQuickstart` | Minimal connect, place, cancel |
| `./gradlew runFullTraderExample` | Reference flow: callbacks, place / modify / cancel, mass-quote / batch-cancel |

## Gradle integration (your own bot)

Add the JAR to your Gradle module (path is relative to that
module's `build.gradle.kts`; adjust if the JAR lives elsewhere):

```kotlin
dependencies {
  implementation(files("sdk/lib/godark-0.1.0-all.jar"))
}
```

Match the filename under `sdk/lib/` to the version shipped in this bundle.
