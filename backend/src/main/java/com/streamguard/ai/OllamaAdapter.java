package com.streamguard.ai;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adapter: sends the internal contract through the authenticated local-model gateway. */
@Component
public class OllamaAdapter implements AiGateway {
  private final ObjectMapper json;
  private final String url;
  private final String token;
  private final String model;
  private final HttpClient http =
      HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofSeconds(8))
          .build();

  public OllamaAdapter(
      ObjectMapper json,
      @Value("${app.ollama-url}") String url,
      @Value("${app.ollama-token}") String token,
      @Value("${app.ollama-model}") String model) {
    this.json = json;
    this.url = url.replaceAll("/+$", "");
    this.token = token;
    this.model = model;
    if (!url.isBlank()) {
      var address = URI.create(url);
      boolean loopback =
          "http".equals(address.getScheme())
              && java.util.Set.of("localhost", "127.0.0.1", "::1").contains(address.getHost());
      if ((!"https".equals(address.getScheme()) && !loopback)
          || address.getUserInfo() != null
          || address.getQuery() != null
          || address.getFragment() != null
          || !java.util.Set.of("", "/").contains(address.getPath()))
        throw new IllegalArgumentException("AI gateway must use HTTPS or loopback HTTP");
    }
  }

  public boolean configured() {
    return !url.isBlank() && token.length() >= 32;
  }

  public String model() {
    return model;
  }

  @org.springframework.context.event.EventListener(
      org.springframework.boot.context.event.ApplicationReadyEvent.class)
  public void diagnoseConnection() {
    if (!configured() || !"true".equals(System.getenv("AI_CONNECTION_CHECK"))) return;
    var log = org.slf4j.LoggerFactory.getLogger(OllamaAdapter.class);
    try {
      log.info(
          "AI gateway DNS: {}",
          java.util.Arrays.toString(java.net.InetAddress.getAllByName(URI.create(url).getHost())));
    } catch (Exception e) {
      log.warn("AI gateway DNS failed: {}", e.toString());
    }
    try {
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(url + "/health"))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      log.info("AI gateway HttpClient anonymous health HTTP {}", response.statusCode());
    } catch (Exception e) {
      log.warn("AI gateway HttpClient health failed: {}", e.toString());
      if (e.getCause() != null)
        log.warn("AI gateway HttpClient cause: {}", e.getCause().toString());
    }
    try {
      var connection =
          (java.net.HttpURLConnection) URI.create(url + "/health").toURL().openConnection();
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(10000);
      try {
        log.info("AI gateway URLConnection anonymous health HTTP {}", connection.getResponseCode());
      } finally {
        connection.disconnect();
      }
    } catch (Exception e) {
      log.warn("AI gateway URLConnection health failed: {}", e.toString());
    }
  }

  public JsonNode generate(String instruction, JsonNode input, JsonNode schema) {
    if (!configured()) throw new IllegalStateException("Local AI gateway is not configured");
    try {
      var request =
          HttpRequest.newBuilder(URI.create(url + "/generate"))
              .timeout(Duration.ofSeconds(50))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + token)
              .header("ngrok-skip-browser-warning", "1")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of(
                              "instruction",
                              instruction,
                              "input",
                              input,
                              "schema",
                              schema,
                              "model",
                              model))))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        byte[] bytes = body.readNBytes(65537);
        if (response.statusCode() != 200 || bytes.length > 65536)
          throw new IllegalStateException(
              "Local AI gateway returned HTTP "
                  + response.statusCode()
                  + " or an oversized response");
        var envelope = json.readTree(bytes);
        if (!model.equals(envelope.path("model").asText()) || !envelope.path("output").isObject())
          throw new IllegalStateException("Local AI model or output does not match the contract");
        return envelope.path("output");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Local AI request interrupted", e);
    } catch (java.io.IOException e) {
      throw new IllegalStateException(
          "Could not obtain a valid local AI response: "
              + e.getClass().getSimpleName()
              + ": "
              + e.getMessage(),
          e);
    }
  }
}
