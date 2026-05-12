package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import godark.GodarkClient;
import godark.GodarkException;
import godark.TransportConfig;
import godark.Types;

/** Minimal MM example: far-from-market LIMIT SELL then cancel. */
public final class Quickstart {

  private static final String SYMBOL = "BTC-USDC-PERP";

  private Quickstart() {}

  public static void main(String[] args) throws Exception {
    String apiKeyId = ExamplesEnv.first("GODARK_API_KEY_ID", "GDX_API_KEY_ID");
    String apiSecret = ExamplesEnv.first("GODARK_API_SECRET", "GDX_API_SECRET");
    if (apiKeyId == null
        || apiKeyId.isBlank()
        || apiSecret == null
        || apiSecret.isBlank()) {
      System.err.println(
          "Missing credentials: set GODARK_API_KEY_ID and GODARK_API_SECRET "
              + "(e.g. in a .env file at the repo root or under examples/).");
      System.exit(1);
      return;
    }

    String base =
        ExamplesEnv.firstOrDefault("wss://api.godark-dex.com", "GODARK_EDGE_URL", "GDX_EDGE_URL");

    GodarkClient.Builder b = GodarkClient.builder().baseUrl(base).apiKeyId(apiKeyId).apiSecret(apiSecret);
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
        Types.OrderAck ack =
            client.placeOrder(
                SYMBOL, "SELL", "LIMIT", 0.01, 999_999.0, "GTC", false, null, null);
        System.out.println("Place OK — order_id=" + ack.orderId());
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
