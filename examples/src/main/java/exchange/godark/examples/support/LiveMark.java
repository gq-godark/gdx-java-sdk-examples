package exchange.godark.examples.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * BTC-USDC-PERP mark for the samples. A price env overrides; otherwise the mark is Devnet open
 * interest notional divided by size for symbol 1. Prices are decimal strings snapped to the 0.5
 * tick so a post-only order cannot cross.
 */
public final class LiveMark {

  public static final BigDecimal TICK = new BigDecimal("0.5");
  public static final BigDecimal OFFSET = new BigDecimal("500");
  public static final String QTY = "0.001";
  public static final long BTC_SYMBOL_ID = 1L;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LiveMark() {}

  /** Positive mark. Throws when no price env is set and open interest has no usable symbol-1 mark. */
  public static BigDecimal resolve(String edgeOrRest) throws Exception {
    String raw = ExamplesEnv.first("GODARK_E2E_PRICE", "GDX_E2E_PRICE", "GDX_LIVE_PRICE");
    if (raw != null && !raw.isBlank()) {
      return positive(raw, "price env");
    }
    String origin = httpOrigin(edgeOrRest);
    String url = origin + "/api/v1/market-data/open-interest";
    HttpClient http =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json")
            .GET()
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("open interest HTTP " + response.statusCode() + " from " + url);
    }
    JsonNode root = MAPPER.readTree(response.body());
    if (root == null || !root.isArray()) {
      throw new IllegalStateException("open interest response is not an array");
    }
    for (JsonNode row : root) {
      if (row.path("symbol_id").asLong(-1) != BTC_SYMBOL_ID) {
        continue;
      }
      BigDecimal notional = positive(row.path("oi_ccy").asText(""), "oi_ccy");
      BigDecimal size = positive(row.path("open_interest").asText(""), "open_interest");
      return notional.divide(size, 8, RoundingMode.HALF_UP);
    }
    throw new IllegalStateException("no open-interest mark for symbol " + BTC_SYMBOL_ID);
  }

  /** Post-only sell: at least {@code mark + 500}, snapped up to the 0.5 tick. */
  public static String sellPrice(BigDecimal mark) {
    BigDecimal min = mark.add(OFFSET);
    BigDecimal steps = min.divide(TICK, 0, RoundingMode.CEILING);
    return steps.multiply(TICK).setScale(1, RoundingMode.UNNECESSARY).toPlainString();
  }

  /**
   * Post-only buy: at least {@code 500 + extraBelow} under the mark, snapped down to the 0.5 tick.
   */
  public static String buyPrice(BigDecimal mark, BigDecimal extraBelow) {
    BigDecimal max = mark.subtract(OFFSET).subtract(extraBelow);
    if (max.signum() <= 0) {
      throw new IllegalStateException("buy price would be non-positive");
    }
    BigDecimal steps = max.divide(TICK, 0, RoundingMode.FLOOR);
    return steps.multiply(TICK).setScale(1, RoundingMode.UNNECESSARY).toPlainString();
  }

  /** {@code wss://host/ws/v1} or an https origin, without a trailing slash. */
  public static String httpOrigin(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("no edge or REST URL for mark lookup");
    }
    String u = raw.strip().replaceAll("/+$", "");
    if (u.startsWith("wss://")) {
      u = "https://" + u.substring("wss://".length());
    } else if (u.startsWith("ws://")) {
      u = "http://" + u.substring("ws://".length());
    }
    if (u.endsWith("/ws/v1")) {
      u = u.substring(0, u.length() - "/ws/v1".length());
    }
    return u.replaceAll("/+$", "");
  }

  private static BigDecimal positive(String raw, String label) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("missing " + label);
    }
    BigDecimal value;
    try {
      value = new BigDecimal(raw.strip());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("invalid " + label);
    }
    if (value.signum() <= 0) {
      throw new IllegalStateException(label + " must be positive");
    }
    return value;
  }
}
