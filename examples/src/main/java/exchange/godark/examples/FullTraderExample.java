package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import exchange.godark.examples.support.LiveMark;
import exchange.godark.examples.support.SampleOrders;
import godark.ConnectionException;
import godark.Environment;
import godark.GodarkClient;
import godark.GodarkException;
import godark.TransportConfig;
import godark.Types;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Trader reference example: sequencer push callbacks, LIMIT place / modify / cancel, session
 * summary.
 */
public final class FullTraderExample {

  private static final String SYMBOL = "BTC-USDC-PERP";

  private FullTraderExample() {}

  public static void main(String[] args) throws Exception {
    String sep = "=".repeat(60);
    System.out.println(sep);
    System.out.println("  GoDark Java SDK — Trader Reference Example");
    System.out.println(sep);
    System.out.println("Order-type support in this sample: post-only LIMIT");

    String legacyKey = ExamplesEnv.first("GODARK_API_KEY", "GDX_API_KEY");

    String baseOverride = ExamplesEnv.first("GODARK_EDGE_URL", "GDX_EDGE_URL");
    String base =
        baseOverride != null && !baseOverride.isBlank()
            ? baseOverride
            : Environment.TESTNET.edgeBaseUrl();
    System.out.println("Endpoint: " + base);

    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Trader-Tag", "java-mm-full-trader");

    TransportConfig transport =
        TransportConfig.DEFAULT
            .withAdditionalHeaders(headers)
            .withOpenTimeout(Duration.ofSeconds(10))
            .withCommandTimeout(Duration.ofSeconds(10))
            .withHeartbeatInterval(Duration.ofSeconds(30))
            .withStaleTimeout(Duration.ofSeconds(120))
            .withMissedHeartbeatLimit(2);
    if (GodarkClient.wsUrl(base).startsWith("wss://")
        && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
      transport = transport.withSslContext(InsecureSsl.context());
    }

    Map<String, Integer> counts = new HashMap<>();
    ArrayDeque<Types.OrderUpdate> orderEvents = new ArrayDeque<>();
    ArrayDeque<String> nonFatal = new ArrayDeque<>(32);

    GodarkClient.Builder b =
        GodarkClient.builder()
            .environment(Environment.TESTNET)
            .transport(transport);
    if (legacyKey != null && !legacyKey.isBlank()) {
      b.apiKey(legacyKey);
    } else {
      String apiKeyId = ExamplesEnv.first("GODARK_API_KEY_ID", "GDX_API_KEY_ID");
      String apiSecret = ExamplesEnv.first("GODARK_API_SECRET", "GDX_API_SECRET");
      String passphrase = ExamplesEnv.first("GODARK_PASSPHRASE", "GDX_PASSPHRASE");
      if (apiKeyId == null
          || apiKeyId.isBlank()
          || apiSecret == null
          || apiSecret.isBlank()
          || passphrase == null
          || passphrase.isBlank()) {
        System.err.println(
            "Missing GODARK_API_KEY_ID / GODARK_API_SECRET / GODARK_PASSPHRASE "
                + "or legacy GODARK_API_KEY for localnet.");
        System.exit(1);
        return;
      }
      b.apiKeyId(apiKeyId).apiSecret(apiSecret).passphrase(passphrase);
    }
    if (baseOverride != null && !baseOverride.isBlank()) {
      b.baseUrl(baseOverride);
    }
    String accountCfg = ExamplesEnv.first("GODARK_ACCOUNT", "GDX_ACCOUNT");
    if (accountCfg != null && !accountCfg.isBlank()) {
      b.account(accountCfg);
    }

    GodarkClient client = b.build();

    client.onOrderUpdate(
        u -> {
          counts.merge("order_update", 1, Integer::sum);
          if (orderEvents.size() >= 50) {
            orderEvents.removeFirst();
          }
          orderEvents.addLast(u);
        });
    client.onPositionUpdate(
        u -> {
          counts.merge("position_update", 1, Integer::sum);
          System.out.printf(
              "POS    side=%s  size=%s  entry=%s%n",
              u.side(), u.size(), u.entryPrice());
        });
    client.onPositionsSnapshot(
        s -> {
          counts.merge("positions_snapshot", 1, Integer::sum);
          System.out.printf(
              "SNAP   source=%s  rows=%d  ts=%d%n",
              s.source(), s.rows().size(), s.serverTimestamp());
          for (Types.PositionRow row : s.rows()) {
            String mark = row.markPrice() != null && !row.markPrice().isBlank() ? row.markPrice() : "—";
            System.out.printf(
                "  ↳ symbol=%d  side=%s  size=%s  entry=%s  mark=%s%n",
                row.symbolId(), row.side(), row.size(), row.entryPrice(), mark);
          }
        });
    client.onSystemHealth(
        h -> {
          counts.merge("system_health", 1, Integer::sum);
          System.out.printf(
              "HEALTH component=%s  state=%d  serving=%s  cause=%s%n",
              h.componentId(), h.state(), h.serving(), h.cause());
        });
    client.onBalanceUpdate(
        bal -> {
          counts.merge("balance_update", 1, Integer::sum);
          System.out.printf("BAL    shielded_raw=%d%n", bal.shieldedBalanceRaw());
        });
    client.onMarginAlert(
        a -> {
          counts.merge("margin_alert", 1, Integer::sum);
          System.out.printf(
              "MARGIN symbol=%d  tier=%d  ratio_bps=%d%n",
              a.symbolId(), a.tier(), a.marginRatioBps());
        });
    client.onFundingRateUpdate(
        fu -> {
          counts.merge("funding_rate", 1, Integer::sum);
          System.out.printf(
              "FUND   symbol=%d  rate=%s  last=%s%n",
              fu.symbolId(), fu.fundingRate(), fu.lastFundingRate());
        });
    client.onSettlementUpdate(
        su -> {
          counts.merge("settlement", 1, Integer::sum);
          System.out.printf("SETTLE batch=%d  status=%s%n", su.batchId(), su.status());
        });
    client.onLeverageSettings(
        ls -> {
          counts.merge("leverage_settings", 1, Integer::sum);
          String rows =
              ls.settings().stream()
                  .limit(5)
                  .map(r -> r.symbolId() + "=" + r.leverage() + "x")
                  .reduce((a, row) -> a + ", " + row)
                  .orElse("");
          String suffix = ls.settings().size() > 5 ? "..." : "";
          System.out.printf("LEVERAGE settings=[%s%s]%n", rows, suffix);
        });
    client.onError(
        e -> {
          while (nonFatal.size() >= 32) {
            nonFatal.removeFirst();
          }
          String msg = String.valueOf(e.getMessage());
          nonFatal.addLast(msg);
          if (e instanceof ConnectionException && msg.contains("stale heartbeat")) {
            System.err.println(
                "STALE HEARTBEAT (non-fatal, auto-reconnect expected): " + msg);
            return;
          }
          System.err.println("SDK ERROR (non-fatal): " + msg);
        });

    System.out.println("Connecting...");
    try {
      client.connect();
    } catch (GodarkException e) {
      System.err.println("Failed to connect: " + e.getMessage());
      System.exit(1);
      return;
    }

    String account = client.account().orElse("");
    System.out.println("Authenticated as account=" + account + "  (session encrypted)");

    try {
      client.subscribe("orders", "positions");
    } catch (GodarkException e) {
      System.err.println("Subscribe failed: " + e.getMessage());
      client.disconnect();
      System.exit(1);
      return;
    }

    System.out.println("Subscribed to order + position updates");
    try {
      TimeUnit.MILLISECONDS.sleep(350);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted");
      System.exit(1);
      return;
    }

    int code = 0;
    try {
      runSession(client, base, counts, orderEvents, nonFatal, sep);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      code = 1;
    } finally {
      client.disconnect();
    }
    if (code != 0) {
      System.exit(code);
      return;
    }
    System.out.println("Disconnected cleanly");
  }

  private static void drainOrders(String label, ArrayDeque<Types.OrderUpdate> orderEvents) {
    int n = orderEvents.size();
    while (!orderEvents.isEmpty()) {
      Types.OrderUpdate u = orderEvents.removeFirst();
      StringBuilder badges = new StringBuilder();
      if (u.cancelReason() != null) {
        badges.append("  cancel_reason=").append(u.cancelReason());
      }
      if (u.reduceOnly()) {
        badges.append("  reduce_only=true");
      }
      if (u.postOnly()) {
        badges.append("  post_only=true");
      }
      System.out.printf(
          "ORDER  %s  id=%s  status=%s  filled=%s  remaining=%s%s%n",
          u.updateType(),
          u.orderId(),
          u.status(),
          u.filledQty(),
          u.remainingQty(),
          badges);
    }
    if (n > 0) {
      System.out.printf("  (%d order update(s) %s)%n", n, label);
    }
  }

  private static void rememberQuoteIds(Types.MassQuoteAck ack, List<String> placed)
      throws GodarkException {
    if (ack == null) {
      throw new GodarkException("mass quote returned no ack");
    }
    boolean failed = !ack.success();
    for (Types.MassQuoteLegResult leg : ack.results()) {
      System.out.printf(
          "  leg %d: status=%s new_order_id=%s fills=%d err=%s%n",
          leg.legIndex(), leg.status(), leg.newOrderId(), leg.fillCount(), leg.errorCode());
      String id = leg.newOrderId();
      if (id != null && !id.isBlank()) {
        placed.add(id);
      }
      if (!"open".equals(leg.status()) || leg.fillCount() != 0 || id == null || id.isBlank()) {
        failed = true;
      }
    }
    if (failed) {
      throw new GodarkException("mass quote leg failed");
    }
  }

  private static void runSession(
      GodarkClient client,
      String base,
      Map<String, Integer> counts,
      ArrayDeque<Types.OrderUpdate> orderEvents,
      ArrayDeque<String> nonFatal,
      String sep)
      throws Exception {

    BigDecimal mark = LiveMark.resolve(base);
    String buyPrice = LiveMark.buyPrice(mark, BigDecimal.ZERO);
    String modifyPrice = LiveMark.buyPrice(mark, new BigDecimal("100"));
    String sellPrice = LiveMark.sellPrice(mark);
    String ladderA = LiveMark.buyPrice(mark, BigDecimal.ZERO);
    String ladderB = LiveMark.buyPrice(mark, new BigDecimal("500"));
    String ladderC = LiveMark.buyPrice(mark, new BigDecimal("1000"));
    System.out.printf(
        "mark=%s buy=%s modify=%s sell=%s ladder=%s / %s / %s%n",
        mark.toPlainString(), buyPrice, modifyPrice, sellPrice, ladderA, ladderB, ladderC);
    SampleOrders.requireFlat(base);

    List<String> placed = new ArrayList<>();
    Exception failure = null;
    try {
      System.out.println("Setting leverage to 1 via updateLeverage...");
      Types.OrderAck levAck = client.updateLeverage(SYMBOL, 1);
      if (levAck == null || !levAck.success()) {
        throw new GodarkException(
            "updateLeverage failed: " + (levAck == null ? "null" : levAck.error()));
      }
      System.out.printf(
          "updateLeverage: success=%s  order_id=%s%n", levAck.success(), levAck.orderId());

      System.out.printf("Placing post-only limit BUY @ %s qty=%s...%n", buyPrice, LiveMark.QTY);
      String buyId = SampleOrders.place(client, "BUY", LiveMark.QTY, buyPrice, placed);
      System.out.println("BUY placed: order_id=" + buyId);
      TimeUnit.SECONDS.sleep(1);
      drainOrders("after BUY", orderEvents);

      System.out.println("Modifying order price to " + modifyPrice + "...");
      Types.OrderAck modAck = client.modifyOrder(buyId, SYMBOL, modifyPrice, null);
      if (modAck == null
          || !modAck.success()
          || modAck.orderId() == null
          || modAck.orderId().isBlank()) {
        throw new GodarkException(
            "modify failed: " + (modAck == null ? "null" : modAck.error()));
      }
      String modifiedId = modAck.orderId();
      if (!modifiedId.equals(buyId)) {
        placed.remove(buyId);
        if (!placed.contains(modifiedId)) {
          placed.add(modifiedId);
        }
      }
      System.out.println("Modified: order_id=" + modifiedId);
      TimeUnit.SECONDS.sleep(1);
      drainOrders("after MODIFY", orderEvents);
      System.out.println("Cancelling modified BUY...");
      SampleOrders.cancelAll(client, placed);
      drainOrders("after BUY cancel", orderEvents);

      System.out.printf("Placing post-only limit SELL @ %s qty=%s...%n", sellPrice, LiveMark.QTY);
      String sellId = SampleOrders.place(client, "SELL", LiveMark.QTY, sellPrice, placed);
      System.out.println("SELL placed: order_id=" + sellId);
      SampleOrders.cancelAll(client, placed);
      drainOrders("after SELL/CANCEL", orderEvents);

      System.out.printf(
          "Mass-quoting a 3-level post-only BUY ladder @ %s / %s / %s qty=%s...%n",
          ladderA, ladderB, ladderC, LiveMark.QTY);
      Types.MassQuoteAck mq =
          client.massQuote(
              SYMBOL,
              List.of(
                  new Types.MassQuoteLegInput("BUY", ladderA, LiveMark.QTY),
                  new Types.MassQuoteLegInput("BUY", ladderB, LiveMark.QTY),
                  new Types.MassQuoteLegInput("BUY", ladderC, LiveMark.QTY)),
              Boolean.TRUE);
      System.out.printf(
          "Mass quote: success=%s sequence=%s legs=%d%n",
          mq.success(), mq.sequence(), mq.results().size());
      rememberQuoteIds(mq, placed);
      System.out.println("Cancelling " + placed.size() + " ladder order(s) by id...");
      SampleOrders.cancelAll(client, placed);
      drainOrders("after ladder cancel", orderEvents);
    } catch (Exception e) {
      failure = e;
    } finally {
      if (!placed.isEmpty()) {
        try {
          SampleOrders.cancelAll(client, placed);
        } catch (Exception cleanup) {
          System.err.println("cleanup cancel failed: " + cleanup.getMessage());
          if (failure == null) {
            failure = cleanup;
          }
        }
      }
      try {
        SampleOrders.flattenAndRequireFlat(client, base, mark);
      } catch (Exception flatError) {
        System.err.println("account not flat after cleanup: " + flatError.getMessage());
        if (failure == null) {
          failure = flatError;
        }
      }
    }
    if (failure != null) {
      throw failure;
    }

    TimeUnit.MILLISECONDS.sleep(350);

    System.out.println(sep);
    System.out.println("  Session complete");
    System.out.printf(
        "  Callback push counts: orders=%d positions=%d snapshots=%d health=%d balance=%d "
            + "margin=%d funding=%d settle=%d leverage=%d%n",
        counts.getOrDefault("order_update", 0),
        counts.getOrDefault("position_update", 0),
        counts.getOrDefault("positions_snapshot", 0),
        counts.getOrDefault("system_health", 0),
        counts.getOrDefault("balance_update", 0),
        counts.getOrDefault("margin_alert", 0),
        counts.getOrDefault("funding_rate", 0),
        counts.getOrDefault("settlement", 0),
        counts.getOrDefault("leverage_settings", 0));
    for (String msg : nonFatal) {
      System.out.println("SDK ERROR (non-fatal): " + msg);
    }
    System.out.println("  Non-fatal callbacks: " + nonFatal.size());
    System.out.println(sep);
  }
}
