package exchange.godark.examples;

import exchange.godark.examples.support.ExamplesEnv;
import exchange.godark.examples.support.InsecureSsl;
import exchange.godark.examples.support.LiveMark;
import exchange.godark.examples.support.SampleOrders;
import godark.Environment;
import godark.GodarkClient;
import godark.GodarkException;
import godark.TransportConfig;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Minimal MM example: post-only LIMIT SELL priced off the live mark, then cancel that order. */
public final class Quickstart {

  private Quickstart() {}

  public static void main(String[] args) throws Exception {
    String legacyKey = ExamplesEnv.first("GODARK_API_KEY", "GDX_API_KEY");

    String baseOverride = ExamplesEnv.first("GODARK_EDGE_URL", "GDX_EDGE_URL");
    String base =
        baseOverride != null && !baseOverride.isBlank()
            ? baseOverride
            : Environment.TESTNET.edgeBaseUrl();

    GodarkClient.Builder b = GodarkClient.builder().environment(Environment.TESTNET);
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
            "Missing credentials: set GODARK_API_KEY_ID/GODARK_API_SECRET/GODARK_PASSPHRASE "
                + "or legacy GODARK_API_KEY for localnet.");
        System.exit(1);
        return;
      }
      b.apiKeyId(apiKeyId).apiSecret(apiSecret).passphrase(passphrase);
    }
    if (baseOverride != null && !baseOverride.isBlank()) {
      b.baseUrl(baseOverride);
    }
    String account = ExamplesEnv.first("GODARK_ACCOUNT", "GDX_ACCOUNT");
    if (account != null && !account.isBlank()) {
      b.account(account);
    }
    if (GodarkClient.wsUrl(base).startsWith("wss://")
        && ExamplesEnv.truthy("GODARK_TLS_SKIP_VERIFY", "GDX_TLS_SKIP_VERIFY")) {
      b.transport(TransportConfig.DEFAULT.withSslContext(InsecureSsl.context()));
    }

    BigDecimal mark;
    try {
      mark = LiveMark.resolve(base);
    } catch (Exception e) {
      System.err.println("No live mark; placing nothing: " + e.getMessage());
      System.exit(1);
      return;
    }
    String sellPrice = LiveMark.sellPrice(mark);
    System.out.printf("mark=%s  post-only SELL @ %s qty=%s%n", mark.toPlainString(), sellPrice, LiveMark.QTY);

    try {
      SampleOrders.requireFlat(base);
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }

    GodarkClient client = b.build();
    List<String> placed = new ArrayList<>();
    int code = 0;
    try {
      client.connect();
      String connectedAccount = client.account().orElse("");
      System.out.println("Connected as account=" + connectedAccount);
      try {
        // Book confirmation waits on private order updates; subscribe first.
        client.subscribe("orders", "positions");
        Thread.sleep(350);
        String orderId = SampleOrders.place(client, "SELL", LiveMark.QTY, sellPrice, placed);
        System.out.printf("Place OK — order_id=%s (post-only limit SELL @ %s)%n", orderId, sellPrice);
        SampleOrders.cancelAll(client, placed);
        SampleOrders.flattenAndRequireFlat(client, base, mark);
      } catch (Exception e) {
        System.err.println("Order rejected: " + e.getMessage());
        try {
          SampleOrders.cancelAll(client, placed);
        } catch (Exception cancelError) {
          System.err.println("cleanup cancel failed: " + cancelError.getMessage());
        }
        try {
          SampleOrders.flattenAndRequireFlat(client, base, mark);
        } catch (Exception flatError) {
          System.err.println("account not flat after cleanup: " + flatError.getMessage());
        }
        code = 1;
      }
    } catch (GodarkException e) {
      System.err.println(e.getMessage());
      code = 1;
    } finally {
      client.disconnect();
    }
    if (code != 0) {
      System.exit(code);
      return;
    }
    System.out.println("Disconnected");
  }
}
