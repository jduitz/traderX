package finos.traderx.tradeprocessor.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class RuntimeConfigTests {
  @Test
  void configuredReferenceDataReadTimeoutIsApplied() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/slow", exchange -> {
      try {
        Thread.sleep(250);
        exchange.sendResponseHeaders(200, 0);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    });
    server.start();
    try {
      RestTemplate restTemplate = new RuntimeConfig().restTemplate(
          new RestTemplateBuilder(), 100, 50);
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

      assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
          .isInstanceOf(ResourceAccessException.class);
    } finally {
      server.stop(0);
    }
  }
}
