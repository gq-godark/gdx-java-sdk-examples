package exchange.godark.examples;

import com.fasterxml.jackson.databind.JsonNode;
import exchange.godark.examples.support.ExamplesEnv;
import godark.GodarkException;
import godark.GodarkRestClient;
import godark.Types;

/**
 * Minimal GodarkRestClient demo — public market-data GETs + REST auth + encrypted snapshots.
 *
 * <p>For encrypted place/modify/cancel over REST (one-shot HPKE), see RestTraderExample.
 *
 * <pre>
 *   ./gradlew -p examples runRestClientExample
 * </pre>
 *
 * <p>Environment: GODARK_API_KEY_ID, GODARK_API_SECRET, GODARK_PASSPHRASE; optional GODARK_REST_URL.
 */
public final class RestClientExample {

  private RestClientExample() {}

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
          "Missing credentials: set GODARK_API_KEY_ID, GODARK_API_SECRET and GODARK_PASSPHRASE.");
      System.exit(1);
      return;
    }

    GodarkRestClient.Builder builder =
        GodarkRestClient.builder()
            .apiKeyId(apiKeyId)
            .apiSecret(apiSecret)
            .passphrase(passphrase);
    String restBase = ExamplesEnv.first("GODARK_REST_URL", "GDX_REST_URL");
    if (restBase != null && !restBase.isBlank()) {
      builder.restBaseUrl(restBase);
    }

    try (GodarkRestClient client = builder.build()) {
      JsonNode rates = client.getFundingRates();
      JsonNode oi = client.getOpenInterest();
      JsonNode vol = client.getVolume();
      System.out.printf("funding_rates: %d symbols%n", rates.size());
      System.out.printf("open_interest: %d symbols%n", oi.size());
      JsonNode syms = vol.get("symbols");
      int symCount = syms != null && syms.isArray() ? syms.size() : 0;
      System.out.printf(
          "volume: total_24h=%s symbols=%d%n", vol.path("total_volume_24h").asText("?"), symCount);

      System.out.println("connecting (REST auth/token)...");
      client.connect();
      System.out.printf(
          "identity user_uuid=%s scope=%s%n",
          client.userUuid().orElse("?"), client.tokenScope().orElse(""));

      try {
        Types.OpenOrdersSnapshot open = client.getOpenOrders();
        System.out.printf("open_orders: %d rows%n", open.rows().size());
      } catch (GodarkException e) {
        System.out.println("getOpenOrders skipped: " + e.getMessage());
      }

      try {
        Types.AccountMarginUpdate account = client.getAccount();
        if (account.account() != null) {
          System.out.printf(
              "account total_collateral=%s%n", account.account().totalCollateral());
        }
      } catch (GodarkException e) {
        System.out.println("getAccount skipped: " + e.getMessage());
      }

      System.out.println("REST reads succeeded.");
      System.out.println("For REST trading (place/modify/cancel), see RestTraderExample.");
    }
  }
}
