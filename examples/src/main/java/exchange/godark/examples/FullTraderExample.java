package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import godark.GodarkClient;
import godark.GodarkException;
import godark.GodarkRestClient;
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
          "Missing GODARK_API_KEY_ID / GODARK_API_SECRET / GODARK_PASSPHRASE (.env at repo root).");
      System.exit(1);
      return;
    }

    String base =
        ExamplesEnv.firstOrDefault("wss://api.godark-dex.com", "GODARK_EDGE_URL", "GDX_EDGE_URL");
    System.out.println("Endpoint: " + base);

    {
      String rest = GodarkRestClient.resolveRestBaseUrl(
          ExamplesEnv.first("GODARK_REST_URL", "GDX_REST_URL"));
      GodarkRestClient.Builder rb =
          GodarkRestClient.builder()
              .apiKeyId(apiKeyId)
              .apiSecret(apiSecret)
              .passphrase(passphrase)
              .restBaseUrl(rest);
      if (rest.startsWith("https://")
          && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
        rb.sslContext(InsecureSsl.context());
      }
      GodarkRestClient restClient = rb.build();
      restClient.connect();
      try {
        System.out.printf(
            "Balance: shielded_raw=%d%n", restClient.getMyBalance().shieldedBalanceRaw());
      } finally {
        restClient.close();
      }
    }

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

    GodarkClient.Builder b =
        GodarkClient.builder()
            .baseUrl(base)
            .apiKeyId(apiKeyId)
            .apiSecret(apiSecret)
            .passphrase(passphrase)
            .transport(transport);
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
              "HEALTH nodes=%d  accepting=%s  ready=%d%n",
              h.totalNodes(), h.acceptingOrders(), h.ready());
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
              "FUND   symbol=%d  current=%s  predicted=%s%n",
              fu.symbolId(), fu.currentRate(), fu.predictedRate());
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

  private static void drainOrders(String label, ArrayDeque<Types.OrderUpdate> orderEvents) {
    int n = orderEvents.size();
    while (!orderEvents.isEmpty()) {
      Types.OrderUpdate u = orderEvents.removeFirst();
      System.out.printf(
          "ORDER  %s  id=%s  status=%s  filled=%s  remaining=%s%n",
          u.updateType(), u.orderId(), u.status(), u.filledQty(), u.remainingQty());
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

    System.out.println("Placing limit BUY @ 67500...");
    Types.OrderAck buyAck;
    try {
      buyAck =
          client.placeOrder(
              SYMBOL, "BUY", "LIMIT", 0.1, 67_500.0, "GTC", false, null, null);
      System.out.printf(
          "BUY placed: order_id=%s  sequence=%s%n", buyAck.orderId(), buyAck.sequence());
    } catch (GodarkException e) {
      System.err.println("BUY rejected: " + e.getMessage());
      return;
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after BUY", orderEvents);

    System.out.println("Modifying order price to 68000...");
    try {
      Types.OrderAck modAck = client.modifyOrder(buyAck.orderId(), SYMBOL, 68_000.0, null);
      System.out.println("Modified: order_id=" + modAck.orderId());
    } catch (GodarkException e) {
      System.err.println("Modify rejected: " + e.getMessage());
    }

    TimeUnit.SECONDS.sleep(1);
    drainOrders("after MODIFY", orderEvents);

    System.out.println("Placing limit SELL @ 95000...");
    try {
      Types.OrderAck sellAck =
          client.placeOrder(
              SYMBOL, "SELL", "LIMIT", 0.05, 95_000.0, "GTC", false, null, null);
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
    System.out.println("Mass-quoting a 3-level BUY ladder (post-only)...");
    List<Types.MassQuoteLegInput> ladder =
        List.of(
            new Types.MassQuoteLegInput("BUY", 66_000.0, 0.02),
            new Types.MassQuoteLegInput("BUY", 65_500.0, 0.02),
            new Types.MassQuoteLegInput("BUY", 65_000.0, 0.02));
    List<Long> restingIds = new ArrayList<>();
    try {
      Types.MassQuoteAck mq = client.massQuote(SYMBOL, ladder, 1, null);
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
