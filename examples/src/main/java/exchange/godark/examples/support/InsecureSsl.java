package exchange.godark.examples.support;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Dev/testnet only — installs a trust-all {@link SSLContext} (do not use in production). */
public final class InsecureSsl {

  private InsecureSsl() {}

  public static SSLContext context() throws Exception {
    TrustManager[] trustAll =
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }
          }
        };
    SSLContext sc = SSLContext.getInstance("TLS");
    sc.init(null, trustAll, new SecureRandom());
    return sc;
  }
}
