package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import godark.ConnectionException;
import godark.Enums;
import godark.Environment;
import godark.GodarkClient;
import godark.GodarkException;
import godark.TransportConfig;
import godark.Types;
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
    System.out.println("Order-type support in this distribution: MARKET, LIMIT");

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

    try {
      runSession(client, counts, orderEvents, nonFatal, sep);
    } catch (GodarkException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Interrupted");
      System.exit(1);
      return;
    } finally {
      client.disconnect();
    }
    System.out.println("Disconnected cleanly");
  }

  /** Price override is a decimal string. It is not parsed through {@code double}. */
  private static String priceOr(String literal) {
    String raw = ExamplesEnv.first("GODARK_E2E_PRICE", "GDX_E2E_PRICE", "GDX_LIVE_PRICE");
    if (raw != null && !raw.isBlank()) {
      return raw.strip();
    }
    return literal;
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

  private static void runSession(
      GodarkClient client,
      Map<String, Integer> counts,
      ArrayDeque<Types.OrderUpdate> orderEvents,
      ArrayDeque<String> nonFatal,
      String sep)
      throws GodarkException, InterruptedException {

    System.out.println("Setting leverage to 1 via updateLeverage...");
    try {
      Types.OrderAck levAck = client.updateLeverage(SYMBOL, 1);
      System.out.printf(
          "updateLeverage: success=%s  order_id=%s%n", levAck.success(), levAck.orderId());
    } catch (GodarkException e) {
      System.err.println("updateLeverage rejected: " + e.getMessage());
      return;
    }

    System.out.printf("Placing limit BUY @ %s qty=0.001...%n", priceOr("67500.5"));
    Types.OrderAck buyAck = null;
    try {
      buyAck =
          client.placeOrder(
              SYMBOL, "BUY", "LIMIT", "0.001", priceOr("67500.5"), "GTC", false, null, null);
      System.out.printf(
          "BUY placed: order_id=%s  sequence=%s%n", buyAck.orderId(), buyAck.sequence());
    } catch (GodarkException e) {
      System.err.println("BUY rejected (continuing to market order): " + e.getMessage());
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after BUY", orderEvents);

    if (buyAck != null) {
      System.out.println("Modifying order price to 67400.5...");
      try {
        Types.OrderAck modAck =
            client.modifyOrder(buyAck.orderId(), SYMBOL, "67400.5", null);
        System.out.println("Modified: order_id=" + modAck.orderId());
      } catch (GodarkException e) {
        System.err.println("Modify rejected: " + e.getMessage());
      }
      TimeUnit.SECONDS.sleep(1);
      drainOrders("after MODIFY", orderEvents);
    }

    // A market IOC can fill and leave a position. This sample does not send one.
    System.out.println("Skipping market IOC so the sample does not open a position.");

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after MARKET BUY", orderEvents);

    System.out.println("Placing limit SELL @ 999999 qty=0.001...");
    try {
      Types.OrderAck sellAck =
          client.placeOrder(
              SYMBOL,
              "SELL",
              "LIMIT",
              "0.001",
              "999999",
              "GTC",
              false,
              null,
              null,
              new Types.PlaceOrderOptions(false, true, Enums.stpUnset(), null, null, null, null));
      System.out.println("SELL placed: order_id=" + sellAck.orderId());
      TimeUnit.MILLISECONDS.sleep(500);
      try {
        Types.OrderAck cack = client.cancelOrder(sellAck.orderId(), SYMBOL);
        System.out.println("SELL cancelled: order_id=" + cack.orderId());
      } catch (GodarkException e) {
        System.err.println("Cancel SELL rejected: " + e.getMessage());
      }
    } catch (GodarkException e) {
      System.err.println("SELL rejected: " + e.getMessage());
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after SELL/CANCEL", orderEvents);

    // --- Bulk quote (mass quote) ---
    // Place a whole ladder of resting quotes in one batched request. Passing
    // null (or true) for postOnly keeps post-only behaviour: a leg that would
    // cross is rejected as "failed" so the batch fuses into a single MPC round.
    // Pass Boolean.FALSE for the relaxed path, where a crossing leg takes
    // liquidity up to its limit and rests the remainder (the number of taker
    // fills is reported per leg as fillCount).
    System.out.println(
        "Mass-quoting a 3-level BUY ladder (post-only) @ 67300.5 / 67100.5 / 66900.5...");
    List<Types.MassQuoteLegInput> ladder =
        List.of(
            new Types.MassQuoteLegInput("BUY", "67300.5", "0.02"),
            new Types.MassQuoteLegInput("BUY", "67100.5", "0.02"),
            new Types.MassQuoteLegInput("BUY", "66900.5", "0.02"));
    List<Long> restingIds = new ArrayList<>();
    try {
      Types.MassQuoteAck mq = client.massQuote(SYMBOL, ladder, null);
      System.out.printf(
          "Mass quote: success=%s sequence=%s legs=%d%n",
          mq.success(), mq.sequence(), mq.results().size());
      for (Types.MassQuoteLegResult r : mq.results()) {
        System.out.printf(
            "  leg %d: status=%s new_order_id=%s fills=%d err=%s%n",
            r.legIndex(), r.status(), r.newOrderId(), r.fillCount(), r.errorCode());
        if ("open".equals(r.status()) && r.newOrderId() != null && !r.newOrderId().isBlank()) {
          try {
            restingIds.add(Long.parseLong(r.newOrderId()));
          } catch (NumberFormatException ignore) {
            // non-numeric id; skip cleanup for this leg
          }
        }
      }
    } catch (GodarkException e) {
      System.err.println("Mass quote rejected: " + e.getMessage());
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after MASS QUOTE", orderEvents);

    if (!restingIds.isEmpty()) {
      System.out.println("Cancelling " + restingIds.size() + " ladder order(s) by id...");
      for (Long id : restingIds) {
        try {
          Types.OrderAck ca = client.cancelOrder(Long.toString(id), SYMBOL);
          System.out.println("  cancel order_id=" + ca.orderId());
        } catch (GodarkException e) {
          System.err.println("cancel " + id + " rejected: " + e.getMessage());
        }
      }
      TimeUnit.MILLISECONDS.sleep(500);
      drainOrders("after CANCEL ALL", orderEvents);
    }

    // Crossing BUY is a decimal string above the ladder. post_only=true rejects
    // a would-cross leg; post_only=false may take liquidity.
    // postOnly=true: a crossing leg is rejected (would-cross, error_code 2018).
    System.out.println("Mass-quoting a crossing BUY with post_only=true (expect rejected/2018)...");
    try {
      Types.MassQuoteAck mq =
          client.massQuote(
              SYMBOL,
              List.of(new Types.MassQuoteLegInput("BUY", "70875.5", "0.001")),
              Boolean.TRUE);
      for (Types.MassQuoteLegResult r : mq.results()) {
        System.out.printf(
            "  leg %d: status=%s err=%s fills=%d%n",
            r.legIndex(), r.status(), r.errorCode(), r.fillCount());
      }
    } catch (GodarkException e) {
      System.err.println("post_only=true mass quote rejected: " + e.getMessage());
    }
    TimeUnit.MILLISECONDS.sleep(500);

    // postOnly=false prices below the ladder so the leg rests instead of filling.
    System.out.println(
        "Mass-quoting a resting BUY @ 64000.5 with post_only=false (cancelled by id)...");
    try {
      Types.MassQuoteAck mq =
          client.massQuote(
              SYMBOL,
              List.of(new Types.MassQuoteLegInput("BUY", "64000.5", "0.003")),
              Boolean.FALSE);
      java.util.ArrayList<Long> strayIds = new java.util.ArrayList<>();
      for (Types.MassQuoteLegResult r : mq.results()) {
        System.out.printf(
            "  leg %d: status=%s new_order_id=%s err=%s fills=%d%n",
            r.legIndex(), r.status(), r.newOrderId(), r.errorCode(), r.fillCount());
        if ("open".equals(r.status()) && r.newOrderId() != null && !r.newOrderId().isBlank()) {
          try {
            strayIds.add(Long.parseLong(r.newOrderId()));
          } catch (NumberFormatException ignore) {
            // non-numeric id; skip cleanup for this leg
          }
        }
      }
      if (!strayIds.isEmpty()) {
        System.out.printf(
            "Batch-cancelling %d post_only=false remainder(s)...%n", strayIds.size());
        try {
          Types.BatchCancelAck bc = client.batchCancel(SYMBOL, strayIds);
          for (Types.BatchCancelLegResult r : bc.results()) {
            System.out.printf(
                "  cancel id=%s: cancelled=%s err=%s%n",
                r.orderId(), r.cancelled(), r.errorCode());
          }
        } catch (GodarkException e) {
          System.err.println("post_only=false remainder cancel rejected: " + e.getMessage());
        }
      }
    } catch (GodarkException e) {
      System.err.println("post_only=false mass quote rejected: " + e.getMessage());
    }
    TimeUnit.SECONDS.sleep(1);
    drainOrders("after post_only mass quotes", orderEvents);

    if (buyAck != null) {
      System.out.println("Cancelling original BUY (cleanup)...");
      try {
        client.cancelOrder(buyAck.orderId(), SYMBOL);
        System.out.println("Original BUY cancelled");
      } catch (GodarkException e) {
        System.out.println("Original BUY already filled or cancelled");
      }
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
