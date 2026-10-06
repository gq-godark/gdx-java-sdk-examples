package exchange.godark.examples;

import com.fasterxml.jackson.databind.JsonNode;
import exchange.godark.examples.support.ExamplesEnv;
import godark.GodarkException;
import godark.GodarkRestClient;
import godark.Types;

/**
 * Minimal GodarkRestClient demo — public market-data GETs, REST auth, and account snapshots.
 *
 * <p>For encrypted place/modify/cancel over REST, see the REST trader sample in the SDK.
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
    String configuredAccount = ExamplesEnv.first("GODARK_ACCOUNT", "GDX_ACCOUNT");
    if (configuredAccount != null && !configuredAccount.isBlank()) {
      builder.account(configuredAccount);
    }
    String restBase =
        ExamplesEnv.first(
            "GODARK_REST_URL", "GDX_REST_URL", "GODARK_EDGE_URL", "GDX_EDGE_URL");
    if (restBase != null && !restBase.isBlank()) {
      builder.restBaseUrl(exchange.godark.examples.support.LiveMark.httpOrigin(restBase));
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
          "identity account=%s scope=%s%n",
          client.account().orElse("?"), client.tokenScope().orElse(""));

      Types.PositionsSnapshot positions = client.getPositions();
      Types.OpenOrdersSnapshot open = client.getOpenOrders();
      Types.AccountMarginUpdate account = client.getAccount();
      System.out.printf("positions: %d rows%n", positions.rows().size());
      System.out.printf("open_orders: %d rows%n", open.rows().size());
      String collateral = "?";
      if (account.summary() != null) {
        collateral = account.summary().totalCollateral();
      }
      System.out.printf("account total_collateral=%s%n", collateral);

      System.out.println("REST reads succeeded.");
      System.out.println("For REST trading (place/modify/cancel), see the SDK REST trader.");
    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
