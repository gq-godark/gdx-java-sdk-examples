package exchange.godark.examples;

import com.fasterxml.jackson.databind.JsonNode;
import exchange.godark.examples.support.ExamplesEnv;
import godark.GodarkException;
import godark.GodarkRestClient;
import godark.Types;

/**
 * Minimal GodarkRestClient demo — auth + account reads + public market data.
 *
 * <p>Encrypted place/cancel/modify/updateLeverage require GodarkClient (WebSocket / HPKE); see
 * Quickstart / FullTraderExample.
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
      // Public market-data GETs — no connect() required.
      JsonNode rates = client.getFundingRates();
      JsonNode oi = client.getOpenInterest();
      JsonNode vol = client.getVolume();
      System.out.printf(
          "funding_rates: %d symbols (first=%s)%n",
          rates.size(), rates.size() > 0 ? rates.get(0) : null);
      System.out.printf(
          "open_interest: %d symbols (first=%s)%n", oi.size(), oi.size() > 0 ? oi.get(0) : null);
      System.out.printf(
          "volume: total_24h=%s symbols=%d%n",
          vol.path("total_volume_24h").asText(), vol.path("symbols").size());

      System.out.println("connecting (REST auth/token)...");
      client.connect();

      Types.MeProfile me = client.getMe();
      System.out.printf(
          "me: id=%s wallet=%s tier=%s%n", me.id(), me.walletAddress(), me.tier());

      Types.LeverageSettings lev = client.getLeverage();
      System.out.printf("leverage settings: %d entries%n", lev.settings().size());
      lev.settings().stream()
          .limit(5)
          .forEach(
              row ->
                  System.out.printf(
                      "  symbolId=%d leverage=%d%n", row.symbolId(), row.leverage()));

      try {
        Types.Balance bal = client.getMyBalance();
        System.out.printf(
            "balance: shielded_raw=%d wallet_ui=%s%n",
            bal.shieldedBalanceRaw(), bal.walletUsdtUi());
      } catch (GodarkException e) {
        System.out.println("getMyBalance skipped: " + e.getMessage());
      }

      System.out.println("REST reads succeeded.");
      System.out.println("Encrypted trading requires GodarkClient over WebSocket (HPKE).");
    }
  }
}
