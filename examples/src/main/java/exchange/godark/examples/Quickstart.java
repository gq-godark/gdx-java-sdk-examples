package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import godark.Environment;
import godark.GodarkClient;
import godark.GodarkException;
import godark.TransportConfig;
import godark.Types;

/** Minimal MM example: far-from-market LIMIT SELL then cancel. */
public final class Quickstart {

  private static final String SYMBOL = "BTC-USDC-PERP";

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

  private Quickstart() {}

  public static void main(String[] args) throws Exception {
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
          "Missing credentials: set GODARK_API_KEY_ID, GODARK_API_SECRET and GODARK_PASSPHRASE "
              + "(e.g. in a .env file at the repo root or under examples/).");
      System.exit(1);
      return;
    }

    String baseOverride = ExamplesEnv.first("GODARK_EDGE_URL", "GDX_EDGE_URL");
    String base =
        baseOverride != null && !baseOverride.isBlank()
            ? baseOverride
            : Environment.TESTNET.edgeBaseUrl();

    GodarkClient.Builder b =
        GodarkClient.builder()
            .environment(Environment.TESTNET)
            .apiKeyId(apiKeyId)
            .apiSecret(apiSecret)
            .passphrase(passphrase);
    if (baseOverride != null && !baseOverride.isBlank()) {
      b.baseUrl(baseOverride);
    }
    String uid = ExamplesEnv.first("GODARK_USER_UUID", "GDX_USER_UUID");
    if (uid != null && !uid.isBlank()) {
      b.userUuid(uid);
    }
    if (GodarkClient.wsUrl(base).startsWith("wss://")
        && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
      b.transport(TransportConfig.DEFAULT.withSslContext(InsecureSsl.context()));
    }

    GodarkClient client = b.build();
    try {
      client.connect();
      String user = client.userUuid().orElse("");
      System.out.println("Connected as user_uuid=" + user);
      try {
        // Book confirmation waits on private order updates; subscribe first.
        client.subscribe("orders", "positions");
        Thread.sleep(350);
        double mark = liveMarkPrice();
        double sellPx = Math.round(mark * 1.03 * 10.0) / 10.0;
        Types.OrderAck ack =
            client.placeOrder(
                SYMBOL, "SELL", "LIMIT", 0.01, sellPx, "GTC", false, null, null);
        System.out.printf(
            "Place OK — order_id=%s (limit SELL @ %.1f, mark=%.1f)%n",
            ack.orderId(), sellPx, mark);
        // Allow the resting order to settle before cancel (avoids CANCEL_TOO_SOON).
        Thread.sleep(500);
        Types.OrderAck cancelAck = client.cancelOrder(ack.orderId(), SYMBOL);
        System.out.println("Cancel OK — order_id=" + cancelAck.orderId());
      } catch (GodarkException e) {
        System.err.println("Order rejected: " + e.getMessage());
        System.exit(1);
        return;
      }
    } catch (GodarkException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    } finally {
      client.disconnect();
    }
    System.out.println("Disconnected");
  }
}
