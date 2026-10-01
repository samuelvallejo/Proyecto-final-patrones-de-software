package com.streamguard;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.streamguard.patterns.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PatternsTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void builderRejectsInconsistentThresholds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModerationPolicy.Builder().thresholds(.9, .5).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModerationPolicy.Builder().thresholds(Double.NaN, .8).build());
    assertThrows(
        IllegalArgumentException.class,
        () -> new ModerationPolicy.Builder().muteSeconds(5).build());
  }

  @Test
  void builderProtectsImmutableRules() {
    var words = new ArrayList<>(List.of("prohibido"));
    var p = new ModerationPolicy.Builder().blockedWords(words).build();
    words.clear();
    assertEquals(List.of("prohibido"), p.blockedWords());
    assertThrows(UnsupportedOperationException.class, () -> p.blockedWords().clear());
  }

  @Test
  void localFactoryUsesWordBoundariesAndNormalizesAccents() {
    var p = new ModerationPolicy.Builder().blockedWords(List.of("acción")).build();
    var analyzer = new AiToolkitFactory.LocalToolkit(json).moderation();
    assertEquals("RESTRICTED", analyzer.analyze("Esa ACCION no se permite", p).category());
    assertEquals("SAFE", analyzer.analyze("interacción", p).category());
    assertEquals("LINK", analyzer.analyze("mira https://example.com", p).category());
    assertEquals(
        "SAFE",
        analyzer
            .analyze(
                "mira https://example.com", new ModerationPolicy.Builder().allowLinks(true).build())
            .category());
  }

  @Test
  void geminiFactoryValidatesProviderResponses() {
    var factory =
        new AiToolkitFactory.GeminiToolkit(
            (instruction, input, schema) ->
                json.valueToTree(Map.of("category", "HATE", "confidence", 2, "reason", "Invalid")),
            json);
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.moderation().analyze("mensaje", new ModerationPolicy.Builder().build()));
    var editor =
        new AiToolkitFactory.GeminiToolkit(
            (instruction, input, schema) -> json.valueToTree(Map.of("summary", "Incomplete")),
            json);
    assertThrows(
        IllegalArgumentException.class, () -> editor.editorial().compose(json.createObjectNode()));
  }

  @Test
  void bridgeSeparatesMessageTypeFromDelivery() {
    List<String> sent = new ArrayList<>();
    NotificationBridge.Delivery delivery = (u, type, title, body) -> sent.add(type + ":" + body);
    var user = UUID.randomUUID();
    new NotificationBridge.ClipNotice(delivery).send(user, "clip listo");
    new NotificationBridge.ModerationNotice(delivery).send(user, "mensaje oculto");
    assertEquals(List.of("CLIP:clip listo", "MODERATION:mensaje oculto"), sent);
  }
}
