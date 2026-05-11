package exchange.godark.examples;

import com.google.protobuf.ByteString;
import exchange.godark.gdx.GodarkClient;
import exchange.godark.gdx.Proto;

/**
 * Offline sample: resolve edge URL + symbol map and build a serialized place-order request (wire
 * bytes only — encryption is not applied in Java SDK v0.1).
 */
public final class PlaceOrder {

    private PlaceOrder() {}

    public static void main(String[] args) {
        String userUuid =
                System.getenv().getOrDefault(
                        "GODARK_USER_UUID", "00000000-0000-4000-8000-000000000001");
        GodarkClient client =
                GodarkClient.builder()
                        .apiKey(System.getenv().getOrDefault("GODARK_API_KEY", "offline-demo-key"))
                        .baseUrl(
                                System.getenv()
                                        .getOrDefault("GODARK_EDGE_URL", "wss://api.godark-dex.com"))
                        .userUuid(System.getenv("GODARK_USER_UUID"))
                        .build();

        ByteString uid = Proto.uuidStringToBytes(userUuid);
        ByteString corr = Proto.randomCorrelationId();
        byte[] wire =
                client.buildPlaceOrderWire(
                        "BTC-USDC-PERP",
                        "BUY",
                        "LIMIT",
                        0.01,
                        uid,
                        100_000.0,
                        "GTC",
                        false,
                        null,
                        null,
                        corr);

        System.out.println("WebSocket URL: " + client.webSocketUrl());
        System.out.println("Place-order wire payload: " + wire.length + " bytes");
    }
}
