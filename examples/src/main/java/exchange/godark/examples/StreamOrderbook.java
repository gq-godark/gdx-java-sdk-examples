package exchange.godark.examples;

/**
 * Placeholder for market-data streaming. Full encrypted orderbook / trades streaming will mirror
 * Python {@code MarketDataClient} in a future Java SDK release.
 */
public final class StreamOrderbook {

    private StreamOrderbook() {}

    public static void main(String[] args) {
        System.out.println(
                "Java SDK v0.1: market-data WebSocket streaming is not implemented yet.");
        System.out.println("Use the Python SDK MarketDataClient or gdx-web for orderbook streams.");
    }
}
