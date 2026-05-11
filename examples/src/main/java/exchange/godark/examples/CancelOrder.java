package exchange.godark.examples;

import com.google.protobuf.ByteString;
import exchange.godark.gdx.GodarkClient;
import exchange.godark.gdx.Proto;

/** Offline sample: build a serialized cancel-order request. */
public final class CancelOrder {

    private CancelOrder() {}

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
                        .build();

        long symbolId = client.symbolId("BTC-USDC-PERP");
        ByteString corr = Proto.randomCorrelationId();
        byte[] wire =
                Proto.buildCancelOrderProto(
                        1L, Proto.uuidStringToBytes(userUuid), symbolId, corr);

        System.out.println("Cancel-order wire payload: " + wire.length + " bytes");
    }
}
