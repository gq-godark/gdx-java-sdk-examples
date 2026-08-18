package exchange.godark.examples.support;

import java.util.Arrays;

/**
 * Thin helpers for examples: {@link Dotenv} prefers the OS environment, then {@code .env} /
 * {@code .env.example}. Pass {@code GODARK_*} then {@code GDX_*} so the canonical name wins.
 */
public final class ExamplesEnv {

  private ExamplesEnv() {}

  public static String first(String... keys) {
    return Dotenv.getFirst(keys);
  }

  public static String firstOrDefault(String defaultValue, String... keys) {
    return Dotenv.getFirstOrDefault(defaultValue, keys);
  }

  public static boolean truthy(String... keys) {
    return Dotenv.truthy(keys);
  }

  public static boolean hasFlag(String[] args, String flag) {
    return Arrays.asList(args).contains(flag);
  }

  /** {@code ws://host/ws/v1} → {@code http://host} for HTTP probes. */
  public static String httpOriginFromWs(String wsBase) {
    String u = wsBase.strip().replaceAll("/+$", "");
    if (u.startsWith("wss://")) {
      return "https://" + u.substring(6);
    }
    if (u.startsWith("ws://")) {
      return "http://" + u.substring(5);
    }
    return u;
  }
}
