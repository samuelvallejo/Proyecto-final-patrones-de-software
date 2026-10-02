package com.streamguard;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.streamguard.ai.*;
import com.streamguard.patterns.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalAiGatewayTest {
  private final ObjectMapper json = new ObjectMapper();
  private final String token = "a".repeat(64);
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void adapterAndGatewayTranslateSchemasWithoutExposingModelAdministration() throws Exception {
    var observed = new AtomicReference<JsonNode>();
    var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    upstream.createContext(
        "/api/chat",
        exchange -> {
          observed.set(json.readTree(exchange.getRequestBody()));
          var verdict =
              json.writeValueAsString(
                  Map.of("category", "SAFE", "confidence", 0.05, "reason", "Safe fixture"));
          byte[] bytes =
              json.writeValueAsBytes(
                  Map.of("model", "qwen3:4b", "done", true, "message", Map.of("content", verdict)));
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    upstream.start();
    var gateway =
        new LocalAiGateway(
            0,
            token,
            "qwen3:4b",
            URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
    gateway.start();
    try {
      var adapter =
          new OllamaAdapter(json, "http://127.0.0.1:" + gateway.port(), token, "qwen3:4b");
      var factory = new AiToolkitFactory.OllamaToolkit(adapter, json);
      var verdict = factory.moderation().analyze("Hello", new ModerationPolicy.Builder().build());
      assertEquals("SAFE", verdict.category());
      assertEquals("OLLAMA", verdict.provider());
      assertEquals("object", observed.get().path("format").path("type").asText());
      assertEquals(
          "number",
          observed
              .get()
              .path("format")
              .path("properties")
              .path("confidence")
              .path("type")
              .asText());
      assertFalse(observed.get().path("format").path("additionalProperties").asBoolean());
      assertFalse(observed.get().path("stream").asBoolean());
      assertFalse(observed.get().path("think").asBoolean());
      assertEquals(401, call(gateway.port(), "/generate", "{}", null));
      assertEquals(404, call(gateway.port(), "/api/pull", "{}", token));
      assertEquals(413, call(gateway.port(), "/generate", "x".repeat(98305), token));
      assertEquals(
          400,
          call(
              gateway.port(),
              "/generate",
              json.writeValueAsString(
                  Map.of(
                      "model",
                      "unapproved",
                      "instruction",
                      "Hello",
                      "input",
                      Map.of(),
                      "schema",
                      Map.of())),
              token));
    } finally {
      gateway.stop();
      upstream.stop(0);
    }
  }

  private int call(int port, String path, String body, String key) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
    if (key != null) request.header("Authorization", "Bearer " + key);
    return http.send(
            request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  @Test
  void adapterRejectsUnencryptedRemoteGatewaysAndMissingCredentials() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new OllamaAdapter(json, "http://example.com", token, "qwen3:4b"));
    assertThrows(
        IllegalStateException.class,
        () ->
            new OllamaAdapter(json, "", "", "qwen3:4b")
                .generate("test", json.createObjectNode(), json.createObjectNode()));
  }

  @Test
  void localFactoryRejectsMalformedModelClassifications() {
    var factory =
        new AiToolkitFactory.OllamaToolkit(
            (instruction, input, schema) ->
                json.valueToTree(
                    Map.of("category", "SAFE", "confidence", "high", "reason", "Invalid")),
            json);
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.moderation().analyze("test", new ModerationPolicy.Builder().build()));
  }
}
