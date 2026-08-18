package exchange.godark.examples.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads {@code .env} / {@code .env.example} from the examples tree and repo root.
 *
 * <p>{@link #get(String)} prefers a non-blank {@link System#getenv} value, then the merged files
 * (typical OS-over-file precedence). File search order (later entries override earlier for the
 * same key): parent {@code .env.example}, cwd {@code .env.example}, parent {@code .env}, cwd
 * {@code .env} — so the nearest {@code .env} wins among files.
 */
public final class Dotenv {

  private static final Object LOCK = new Object();
  private static Map<String, String> merged = Map.of();
  private static boolean loaded;

  private Dotenv() {}

  public static void load() {
    synchronized (LOCK) {
      if (loaded) {
        return;
      }
      Path cwd = Paths.get("").toAbsolutePath().normalize();
      Path par = cwd.getParent();
      List<Path> ordered = new ArrayList<>();
      if (par != null) {
        ordered.add(par.resolve(".env.example"));
      }
      ordered.add(cwd.resolve(".env.example"));
      if (par != null) {
        ordered.add(par.resolve(".env"));
      }
      ordered.add(cwd.resolve(".env"));

      LinkedHashMap<String, String> map = new LinkedHashMap<>();
      for (Path p : ordered) {
        try {
          parseInto(map, p);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to read " + p, e);
        }
      }
      merged = Map.copyOf(map);
      loaded = true;
    }
  }

  public static String get(String key) {
    Objects.requireNonNull(key, "key");
    String env = System.getenv(key);
    if (env != null) {
      env = env.strip();
      if (!env.isEmpty()) {
        return env;
      }
    }
    load();
    String v = merged.get(key);
    if (v == null) {
      return null;
    }
    v = v.strip();
    return v.isEmpty() ? null : v;
  }

  /**
   * OS values among {@code keys} first (caller should list {@code GODARK_*} then {@code GDX_*}),
   * then the same keys from merged {@code .env} files.
   */
  public static String getFirst(String... keys) {
    for (String k : keys) {
      String env = System.getenv(k);
      if (env != null) {
        env = env.strip();
        if (!env.isEmpty()) {
          return env;
        }
      }
    }
    load();
    for (String k : keys) {
      String v = merged.get(k);
      if (v == null) {
        continue;
      }
      v = v.strip();
      if (!v.isEmpty()) {
        return v;
      }
    }
    return null;
  }

  public static String getFirstOrDefault(String defaultValue, String... keys) {
    String v = getFirst(keys);
    return v != null ? v : defaultValue;
  }

  public static boolean truthy(String... keys) {
    String v = getFirst(keys);
    if (v == null) {
      return false;
    }
    return !java.util.List.of("0", "false", "no", "off", "").contains(v.toLowerCase());
  }

  private static void parseInto(Map<String, String> into, Path path) throws IOException {
    if (!Files.isRegularFile(path)) {
      return;
    }
    for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("export ")) {
        line = line.substring(7).strip();
      }
      int eq = line.indexOf('=');
      if (eq <= 0) {
        continue;
      }
      String key = line.substring(0, eq).strip();
      if (key.isEmpty()) {
        continue;
      }
      String val = line.substring(eq + 1).strip();
      val = unquote(val);
      into.put(key, val);
    }
  }

  private static String unquote(String val) {
    if (val.length() >= 2) {
      char a = val.charAt(0);
      char b = val.charAt(val.length() - 1);
      if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
        return val.substring(1, val.length() - 1);
      }
    }
    return val;
  }
}
