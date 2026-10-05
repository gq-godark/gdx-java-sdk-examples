package exchange.godark.examples.support;

import godark.Enums;
import godark.GodarkException;
import godark.GodarkRestClient;
import godark.GodarkClient;
import godark.Types;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Post-only place/cancel helpers. Cancels by the id string the place call returned. */
public final class SampleOrders {

  public static final String SYMBOL = "BTC-USDC-PERP";

  private SampleOrders() {}

  public static Types.PlaceOrderOptions postOnly() {
    return new Types.PlaceOrderOptions(false, true, Enums.stpUnset(), null, null, null, null);
  }

  public static Types.PlaceOrderOptions reduceOnly() {
    return new Types.PlaceOrderOptions(true, false, Enums.stpUnset(), null, null, null, null);
  }

  public static String place(
      GodarkClient client, String side, String quantity, String price, List<String> placed)
      throws GodarkException {
    Types.OrderAck ack =
        client.placeOrder(
            SYMBOL, side, "LIMIT", quantity, price, "GTC", false, null, null, postOnly());
    return keep(ack, placed, side + " place");
  }

  public static String keep(Types.OrderAck ack, List<String> placed, String label)
      throws GodarkException {
    String id = ack == null ? null : ack.orderId();
    if (id != null && !id.isBlank() && !placed.contains(id)) {
      placed.add(id);
    }
    if (ack == null || !ack.success() || id == null || id.isBlank()) {
      String detail = ack == null ? "null ack" : String.valueOf(ack.error());
      throw new GodarkException(label + " failed: " + detail);
    }
    return id;
  }

  /** Wait at least one second, then cancel every id still in {@code placed}. Failures stay listed. */
  public static void cancelAll(GodarkClient client, List<String> placed)
      throws GodarkException, InterruptedException {
    if (placed.isEmpty()) {
      return;
    }
    TimeUnit.SECONDS.sleep(1);
    List<String> left = new ArrayList<>();
    GodarkException first = null;
    for (String id : placed) {
      try {
        Types.OrderAck ack = client.cancelOrder(id, SYMBOL);
        if (ack == null || !ack.success()) {
          left.add(id);
          if (first == null) {
            first =
                new GodarkException(
                    "cancel failed for " + id + (ack == null ? "" : ": " + ack.error()));
          }
          continue;
        }
        System.out.println("cancel OK — order_id=" + ack.orderId());
      } catch (GodarkException e) {
        left.add(id);
        if (first == null) {
          first = e;
        }
        System.err.println("cancel " + id + " failed: " + e.getMessage());
      }
    }
    placed.clear();
    placed.addAll(left);
    if (first != null) {
      throw first;
    }
  }

  public static void requireFlat(String edgeOrRest) throws Exception {
    try (GodarkRestClient rest = rest(edgeOrRest)) {
      rest.connect();
      int positions = nonzeroPositions(rest.getPositions());
      int orders = rest.getOpenOrders().rows().size();
      if (positions != 0 || orders != 0) {
        throw new IllegalStateException(
            "account not flat: positions=" + positions + " open_orders=" + orders);
      }
    }
  }

  /**
   * If this process left a position, flatten it with a reduce-only limit (quantity scale at most 4)
   * and require the account to be flat again.
   */
  public static void flattenAndRequireFlat(GodarkClient client, String edgeOrRest, BigDecimal mark)
      throws Exception {
    try (GodarkRestClient rest = rest(edgeOrRest)) {
      rest.connect();
      for (Types.PositionRow row : rest.getPositions().rows()) {
        BigDecimal size = new BigDecimal(row.size());
        if (size.signum() == 0) {
          continue;
        }
        boolean isShort = isShort(row.side().toString(), size);
        BigDecimal qty = size.abs();
        if (qty.scale() > 4) {
          qty = qty.setScale(4, RoundingMode.DOWN);
        }
        if (qty.signum() == 0) {
          throw new IllegalStateException("position size rounds to zero at 4 decimal places");
        }
        String closeSide = isShort ? "BUY" : "SELL";
        String price = isShort ? LiveMark.sellPrice(mark) : LiveMark.buyPrice(mark, BigDecimal.ZERO);
        System.out.printf(
            "Flattening %s %s with reduce-only %s @ %s%n",
            row.side(), qty.toPlainString(), closeSide, price);
        List<String> flattenIds = new ArrayList<>();
        Types.OrderAck ack =
            client.placeOrder(
                SYMBOL,
                closeSide,
                "LIMIT",
                qty.toPlainString(),
                price,
                "GTC",
                true,
                null,
                null,
                reduceOnly());
        keep(ack, flattenIds, "reduce-only flatten");
        try {
          cancelAll(client, flattenIds);
        } catch (Exception cancelError) {
          System.err.println("flatten cancel: " + cancelError.getMessage());
        }
      }
    }
    requireFlat(edgeOrRest);
  }

  private static int nonzeroPositions(Types.PositionsSnapshot snapshot) {
    int n = 0;
    for (Types.PositionRow row : snapshot.rows()) {
      if (new BigDecimal(row.size()).signum() != 0) {
        n++;
      }
    }
    return n;
  }

  private static boolean isShort(String side, BigDecimal size) {
    String name = side.toUpperCase();
    if (name.contains("SHORT") || name.contains("SELL") || name.equals("ASK")) {
      return true;
    }
    if (name.contains("LONG") || name.contains("BUY") || name.equals("BID")) {
      return false;
    }
    return size.signum() < 0;
  }

  private static GodarkRestClient rest(String edgeOrRest) {
    String apiKeyId = ExamplesEnv.first("GODARK_API_KEY_ID", "GDX_API_KEY_ID");
    String apiSecret = ExamplesEnv.first("GODARK_API_SECRET", "GDX_API_SECRET");
    String passphrase = ExamplesEnv.first("GODARK_PASSPHRASE", "GDX_PASSPHRASE");
    if (apiKeyId == null
        || apiKeyId.isBlank()
        || apiSecret == null
        || apiSecret.isBlank()
        || passphrase == null
        || passphrase.isBlank()) {
      throw new IllegalStateException("missing API credentials for account check");
    }
    GodarkRestClient.Builder builder =
        GodarkRestClient.builder().apiKeyId(apiKeyId).apiSecret(apiSecret).passphrase(passphrase);
    String account = ExamplesEnv.first("GODARK_ACCOUNT", "GDX_ACCOUNT");
    if (account != null && !account.isBlank()) {
      builder.account(account);
    }
    builder.restBaseUrl(LiveMark.httpOrigin(edgeOrRest));
    return builder.build();
  }
}
