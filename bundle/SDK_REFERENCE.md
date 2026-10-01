# GoDark Java SDK Reference

This reference describes the API surface used by the bundled examples
shipped in this distribution. The trading examples use persistent WebSocket
encrypted trading via `godark.GodarkClient`. The SDK also provides
`GodarkRestClient` for bearer authentication, one-shot HPKE account snapshots,
and encrypted place / modify / cancel operations. The bundle includes
`RestClientExample` for REST auth, account reads, and public market-data GETs;
it does not include a full REST trader or REST mass-quote / batch wrappers.
A standalone WebSocket market-data client also ships in the JAR but is outside
the bundled examples in this distribution.

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
            .passphrase("...")
            .build()) {
      client.connect();
      client.subscribe("orders", "positions");
      Types.OrderAck ack =
          client.placeOrder(
              "BTC-USDC-PERP",
              "SELL",
              "LIMIT",
              "0.01",
              "999999",
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
- `GDX_HPKE_STATIC_PUBLIC_KEY` (required for localnet/custom encrypted WebSocket trading) — 64 hex chars; aliases `GDX_HPKE_STATIC_PUBKEY`, `GODARK_HPKE_STATIC_PUBLIC_KEY`, `VITE_GDX_HPKE_STATIC_PUBKEY`
- `GODARK_ACCOUNT` (optional) — base58 fallback for custom edges that omit `account` from auth
- `GODARK_EDGE_URL` (optional host origin; client appends `/ws/v1`)

Use the bundle-root `.env.example` as the template (copy to `.env`, or to
`examples/.env` when running Gradle from `examples/`).

### WebSocket transport defaults

`TransportConfig.DEFAULT` is tuned for long-running production clients:

| Field | Default | Meaning |
|-------|---------|---------|
| `heartbeatInterval()` | `30s` | JSON ping interval |
| `staleTimeout()` | `120s` | Absolute silence cap before disconnect |
| `missedHeartbeatLimit()` | `2` | Consecutive missed heartbeat intervals before disconnect |
| `autoReconnect` (builder) | `true` | Reconnect with backoff after unexpected disconnect |

`heartbeatInterval()` is not the disconnect budget. On stale disconnect the SDK reports a
non-fatal `ConnectionException` via `onError` (message contains `stale heartbeat`), then
closes the socket and auto-reconnects unless you called `disconnect()`.

```java
TransportConfig transport =
    TransportConfig.DEFAULT
        .withHeartbeatInterval(Duration.ofSeconds(30))
        .withStaleTimeout(Duration.ofSeconds(120))
        .withMissedHeartbeatLimit(2);
```

## GodarkClient API

**Package:** `godark` (`GodarkClient` is a concrete class; construct with
`GodarkClient.builder()` or legacy `new GodarkClient(apiKey)` for opaque keys.)

### Core lifecycle

| Method | Signature | Purpose |
|--------|-----------|---------|
| `connect` | `void connect() throws GodarkException` | Mint a REST access token, then WebSocket-authenticate with that token |
| `disconnect` | `void disconnect()` | Close socket and reset session |
| `logout` | `void logout() throws GodarkException` | Logout then disconnect |
| `close` | `void close()` | `AutoCloseable` — delegates to `disconnect()` |
| `account` | `Optional<String> account()` | Authenticated base58 account after connect |

### Trading commands

| Method | Signature | Purpose |
|--------|-----------|---------|
| `placeOrder` | `OrderAck placeOrder(String symbol, String side, String orderType, String quantity, String price, String timeInForce, boolean aon, String minFillSize, Long expiryTime) throws GodarkException` | Place encrypted order (decimal strings) |
| `cancelOrder` | `OrderAck cancelOrder(String orderId, String symbol) throws GodarkException` | Cancel by id (overload defaults symbol to `BTC-USDC-PERP`) |
| `modifyOrder` | `OrderAck modifyOrder(String orderId, String symbol, String newPrice, String newQuantity, String newTriggerPrice) throws GodarkException` | Modify price, quantity, and/or stop trigger (decimal strings) |

`side`, `orderType`, and `timeInForce` are **strings** at the command boundary
(for example `"SELL"`, `"LIMIT"`, `"GTC"`). Stream updates use protobuf enums on
the wire (see **Enums**).

### Streams

| Method | Signature | Purpose |
|--------|-----------|---------|
| `subscribe` | `void subscribe(String... channels) throws GodarkException` | `/ws/v1` channels: `orders`, `positions`, `volume`, `open_interest`, `funding_rate`. Unknown channel fails fast. No trades or L2 book. |
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
| `SystemHealthUpdate` | `componentId`, `state`, `serving`, `cause`, `updatedAtNanos`, `sequence`, `schemaVersion` | Component health |
| `BalanceUpdate` | `shieldedBalanceRaw` | Wallet / equity after fills or settlement |
| `MarginAlert` | `owner`, `symbolId`, `tier`, `marginRatioBps`, `markPrice`, `liquidationPrice`, `recovered` | Margin banner per owner and symbol |
| `FundingRateUpdate` | `symbolId`, `fundingRate`, `lastFundingRate`, `timestamp` | Funding ticker / metadata |
| `SettlementUpdate` | `batchId`, `status`, `txSignature`, `affectedUserUuids` | Batch reconciliation |

Each stream uses a single bounded queue per type (default capacity from
`GodarkClient.Builder.streamBufferSize`, typically **256**). When a queue is
full, the oldest entry may be dropped so the client stays live; both the
matching callback and `poll*` observe the same items.

### Concurrency rule

Only one encrypted command (`placeOrder`, `cancelOrder`, `modifyOrder`) should
be in flight at a time. Complete each call (or handle its exception) before
issuing the next.

`Types.PlaceOrderOptions` (optional last argument on `placeOrder`) includes
`reduceOnly`, `postOnly`, `stpMode`, `pegOffsetBps`, `triggerPrice`,
`takeProfitPrice`, `stopLossPrice`, and `slippageBps`. `slippageBps` applies
only to `MARKET` and `STOP_MARKET`. Omit it (null) to use the venue max walk
cap (localnet 5%); typical explicit values are 50–500 bps (0.5%–5%). `PEG` is
not post-only.

WebSocket login uses the REST access token, not `id:secret:passphrase`. A
client-order id is registered only after a successful WebSocket place and is
cached only after `POST /orders/_register_coid` returns HTTP 200. REST place
does not register a client-order id.

## Core Types

**Package:** `godark` — value records in `godark.Types`.

Wire decimals are often exposed as **strings** on push types to preserve
sequencer precision. **Command APIs accept decimal strings only** for prices
and sizes. Pass literals such as quantity `"0.001"` and price `"67500.5"`.

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

Record fields include `account`, `symbolId`, `side`, `updateType`, `size`,
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
- `SessionException` — HPKE setup handshake or encryption session errors
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
| `./gradlew runRestClientExample` | REST auth, account reads, and public market-data GETs (not a REST trader) |

## Gradle integration (your own bot)

Add the JAR to your Gradle module (path is relative to that
module's `build.gradle.kts`; adjust if the JAR lives elsewhere):

```kotlin
dependencies {
  implementation(files("sdk/lib/godark-0.2.0-all.jar"))
}
```

Match the filename under `sdk/lib/` to the version shipped in this bundle.
