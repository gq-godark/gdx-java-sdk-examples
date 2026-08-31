package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import godark.GodarkRestClient;
import godark.Types;
import javax.net.ssl.SSLContext;

/**
 * REST-only trader demo — auth + encrypted snapshots + place/modify/cancel (one-shot HPKE).
 *
 * <pre>
 *   ./gradlew -p examples runRestTraderExample
 * </pre>
 */
public final class RestTraderExample {

  private RestTraderExample() {}

  private static double livePrice() {
    String raw = ExamplesEnv.first("GDX_LIVE_PRICE", "GODARK_LIVE_PRICE");
    if (raw != null && !raw.isBlank()) {
      return Double.parseDouble(raw);
    }
    return 78000.0;
  }

  public static void main(String[] args) throws Exception {
    String cid = ExamplesEnv.first("GODARK_API_KEY_ID", "GDX_API_KEY_ID");
    String sec = ExamplesEnv.first("GODARK_API_SECRET", "GDX_API_SECRET");
    String pass = ExamplesEnv.first("GODARK_PASSPHRASE", "GDX_PASSPHRASE");
    String apiKey = ExamplesEnv.first("GODARK_API_KEY", "GDX_API_KEY");
    if (apiKey == null && (cid == null || sec == null || pass == null)) {
      System.err.println(
          "Missing credentials: set GODARK_API_KEY_ID, GODARK_API_SECRET and GODARK_PASSPHRASE.");
      System.exit(1);
      return;
    }

    String rest =
        ExamplesEnv.firstOrDefault("https://api.godark-dex.com", "GODARK_REST_URL", "GDX_REST_URL");

    SSLContext sslCtx = null;
    if (rest.startsWith("https://") && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
      sslCtx = InsecureSsl.context();
    }

    GodarkRestClient.Builder builder = GodarkRestClient.builder().restBaseUrl(rest);
    if (cid != null && !cid.isBlank() && sec != null && !sec.isBlank() && pass != null && !pass.isBlank()) {
      builder.apiKeyId(cid).apiSecret(sec).passphrase(pass);
    } else if (apiKey != null && !apiKey.isBlank()) {
      builder.apiKey(apiKey);
    } else {
      System.err.println(
          "Missing credentials: set GODARK_API_KEY_ID, GODARK_API_SECRET and GODARK_PASSPHRASE.");
      System.exit(1);
      return;
    }
    if (sslCtx != null) {
      builder.sslContext(sslCtx);
    }

    try (GodarkRestClient client = builder.build()) {
      client.connect();

      System.out.println(
          "identity user_uuid="
              + client.userUuid().orElse("?")
              + " scope="
              + client.tokenScope().orElse(""));

      Types.OpenOrdersSnapshot open = client.getOpenOrders();
      System.out.println("open_orders " + open.rows().size());

      Types.PositionsSnapshot positions = client.getPositions();
      System.out.println("positions " + positions.rows().size());

      Types.AccountMarginUpdate account = client.getAccount();
      if (account.account() != null) {
        System.out.println("account total_collateral=" + account.account().totalCollateral());
      }

      double price = livePrice();
      Types.OrderAck placed =
          client.placeOrder(
              "BTC-USDC-PERP",
              "BUY",
              "LIMIT",
              0.001,
              price,
              "GTC",
              false,
              null,
              null,
              "sdk-java-rest-demo");
      System.out.println(
          "placed order_id=" + placed.orderId() + " success=" + placed.success());

      Thread.sleep(500);

      Types.OrderAck modified =
          client.modifyOrder(placed.orderId(), "BTC-USDC-PERP", price - 64.0, null);
      System.out.println("modified success=" + modified.success());

      Types.OrderAck cancelled = client.cancelOrder(placed.orderId(), "BTC-USDC-PERP");
      System.out.println("cancelled success=" + cancelled.success());
    }
  }
}
