package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
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
            .withCommandTimeout(Duration.ofSeconds(10));
    if (GodarkClient.wsUrl(base).startsWith("wss://")
        && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
      transport = transport.withSslContext(InsecureSsl.context());
    }

    Map<String, Integer> counts = new HashMap<>();
    ArrayDeque<Types.OrderUpdate> orderEvents = new ArrayDeque<>();
    ArrayDeque<String> nonFatal = new ArrayDeque<>(32);
    // BTC-USDC-PERP is symbol id 1; capture its live mark from snapshots so the
    // mass-quote ladder/cross prices can anchor to the real touch. 0 = unseen.
    double[] lastBtcMark = {0.0};

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
    String uidCfg = ExamplesEnv.first("GODARK_USER_UUID", "GDX_USER_UUID");
    if (uidCfg != null && !uidCfg.isBlank()) {
      b.userUuid(uidCfg);
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
            if (row.symbolId() == 1 && row.markPrice() != null && !row.markPrice().isBlank()) {
              try {
                double v = Double.parseDouble(row.markPrice());
                if (v > 0) {
                  lastBtcMark[0] = v;
                }
              } catch (NumberFormatException ignore) {
                // keep previous
              }
            }
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
    client.onError(
        e -> {
          while (nonFatal.size() >= 32) {
            nonFatal.removeFirst();
          }
          nonFatal.addLast(String.valueOf(e.getMessage()));
        });

    System.out.println("Connecting...");
    try {
      client.connect();
    } catch (GodarkException e) {
      System.err.println("Failed to connect: " + e.getMessage());
      System.exit(1);
      return;
    }

    String uid = client.userUuid().orElse("");
    System.out.println("Authenticated as user_uuid=" + uid + "  (session encrypted)");

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
      runSession(client, counts, orderEvents, nonFatal, sep, lastBtcMark);
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

  private static double liveMarkPrice() {
    String raw =
        ExamplesEnv.first("GODARK_E2E_PRICE", "GDX_E2E_PRICE", "GDX_LIVE_PRICE");
    if (raw != null && !raw.isBlank()) {
      try {
        return Double.parseDouble(raw);
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return 79_000.0;
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
      String sep,
      double[] lastBtcMark)
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

    double mark = liveMarkPrice();
    double buyPx = Math.round(mark * 0.997 * 10.0) / 10.0;
    System.out.printf("Placing limit BUY @ %.1f (mark=%.1f)...%n", buyPx, mark);
    Types.OrderAck buyAck;
    try {
      buyAck =
          client.placeOrder(
              SYMBOL, "BUY", "LIMIT", 0.1, buyPx, "GTC", false, null, null);
      System.out.printf(
          "BUY placed: order_id=%s  sequence=%s%n", buyAck.orderId(), buyAck.sequence());
    } catch (GodarkException e) {
      System.err.println("BUY rejected: " + e.getMessage());
      return;
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after BUY", orderEvents);

    double modifyPx = Math.round(mark * 0.996 * 10.0) / 10.0;
    System.out.printf("Modifying order price to %.1f...%n", modifyPx);
    try {
      Types.OrderAck modAck = client.modifyOrder(buyAck.orderId(), SYMBOL, modifyPx, null);
      System.out.println("Modified: order_id=" + modAck.orderId());
    } catch (GodarkException e) {
      System.err.println("Modify rejected: " + e.getMessage());
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after MODIFY", orderEvents);

    double sellPx = Math.round(mark * 1.03 * 10.0) / 10.0;
    System.out.printf("Placing limit SELL @ %.1f...%n", sellPx);
    try {
      Types.OrderAck sellAck =
          client.placeOrder(
              SYMBOL, "SELL", "LIMIT", 0.05, sellPx, "GTC", false, null, null);
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
    // Anchor the ladder/cross to the live BTC mark captured from the snapshot so
    // the crossing demo below is deterministic regardless of current price. Fall
    // back to GDX_BASE (default 64000) only if no mark was seen yet.
    double base = lastBtcMark[0];
    if (base <= 0) {
      base = 64_000.0;
      String baseEnv = ExamplesEnv.first("GDX_BASE");
      if (baseEnv != null && !baseEnv.isBlank()) {
        try {
          double v = Double.parseDouble(baseEnv.strip());
          if (v > 0) {
            base = v;
          }
        } catch (NumberFormatException ignore) {
          // keep default
        }
      }
    }
    System.out.printf("Mass-quoting a 3-level BUY ladder (post-only), base=%.2f...%n", base);
    List<Types.MassQuoteLegInput> ladder =
        List.of(
            new Types.MassQuoteLegInput("BUY", base * (1 - 0.003), 0.02),
            new Types.MassQuoteLegInput("BUY", base * (1 - 0.006), 0.02),
            new Types.MassQuoteLegInput("BUY", base * (1 - 0.009), 0.02));
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
      System.out.printf("Batch-cancelling %d ladder orders (cleanup)...%n", restingIds.size());
      try {
        Types.BatchCancelAck bc = client.batchCancel(SYMBOL, restingIds);
        for (Types.BatchCancelLegResult r : bc.results()) {
          System.out.printf(
              "  cancel id=%s: cancelled=%s err=%s%n", r.orderId(), r.cancelled(), r.errorCode());
        }
      } catch (GodarkException e) {
        System.err.println("Batch cancel rejected: " + e.getMessage());
      }
      TimeUnit.MILLISECONDS.sleep(500);
      drainOrders("after BATCH CANCEL", orderEvents);
    }

    // Demonstrate the batch-level post_only flag on a crossing leg. Price a BUY
    // ~5% above the live mark: aggressive enough to cross the resting ask, yet
    // within the exchange's 10%-of-oracle limit. Anchored to the live mark, this
    // makes the post_only=true (reject) vs false (fill) contrast deterministic.
    double crossPx = base * 1.05;
    // postOnly=true: a crossing leg is rejected (would-cross, error_code 2018).
    System.out.println("Mass-quoting a crossing BUY with post_only=true (expect rejected/2018)...");
    try {
      Types.MassQuoteAck mq =
          client.massQuote(
              SYMBOL, List.of(new Types.MassQuoteLegInput("BUY", crossPx, 0.001)), Boolean.TRUE);
      for (Types.MassQuoteLegResult r : mq.results()) {
        System.out.printf(
            "  leg %d: status=%s err=%s fills=%d%n",
            r.legIndex(), r.status(), r.errorCode(), r.fillCount());
      }
    } catch (GodarkException e) {
      System.err.println("post_only=true mass quote rejected: " + e.getMessage());
    }
    TimeUnit.MILLISECONDS.sleep(500);

    // postOnly=false (relaxed): the crossing leg takes liquidity up to its limit
    // and rests the remainder; taker fills are reported per leg as fillCount.
    System.out.println(
        "Mass-quoting a crossing BUY with post_only=false (expect filled, fills>0)...");
    try {
      Types.MassQuoteAck mq =
          client.massQuote(
              SYMBOL, List.of(new Types.MassQuoteLegInput("BUY", crossPx, 0.003)), Boolean.FALSE);
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

    System.out.println("Cancelling original BUY (cleanup)...");
    try {
      client.cancelOrder(buyAck.orderId(), SYMBOL);
      System.out.println("Original BUY cancelled");
    } catch (GodarkException e) {
      System.out.println("Original BUY already filled or cancelled");
    }

    TimeUnit.MILLISECONDS.sleep(350);

    System.out.println(sep);
    System.out.println("  Session complete");
    System.out.printf(
        "  Callback push counts: orders=%d positions=%d snapshots=%d health=%d balance=%d "
            + "margin=%d funding=%d settle=%d%n",
        counts.getOrDefault("order_update", 0),
        counts.getOrDefault("position_update", 0),
        counts.getOrDefault("positions_snapshot", 0),
        counts.getOrDefault("system_health", 0),
        counts.getOrDefault("balance_update", 0),
        counts.getOrDefault("margin_alert", 0),
        counts.getOrDefault("funding_rate", 0),
        counts.getOrDefault("settlement", 0));
    for (String msg : nonFatal) {
      System.out.println("SDK ERROR (non-fatal): " + msg);
    }
    System.out.println("  Non-fatal callbacks: " + nonFatal.size());
    System.out.println(sep);
  }
}
