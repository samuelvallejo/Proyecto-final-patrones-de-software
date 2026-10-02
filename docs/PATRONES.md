# Patrones aplicados al caso de estudio

Los patrones se usan en caminos ejecutados por la aplicación. Los ejemplos siguientes corresponden a clases presentes en `backend/src/main/java/com/streamguard`.

## Builder · reglas coherentes para cada canal

**Problema:** una política tiene nivel, dos umbrales, silencios, listas de términos y opciones. Un constructor con muchos argumentos facilita combinaciones inválidas.

**Implementación:** `patterns/ModerationPolicy.java`. `Builder` acumula opciones, copia las listas y valida nivel, duración y `reviewThreshold <= blockThreshold` antes de devolver un objeto inmutable. `PlatformService.policy()` lo usa para reconstruir la política persistida; `PlatformController.policy()` lo usa para guardar configuración.

```java
var policy = new ModerationPolicy.Builder()
    .level("STRICT")
    .autoHide(true)
    .autoMute(true)
    .muteSeconds(300)
    .thresholds(0.4, 0.7)
    .blockedWords(List.of("palabra-prueba"))
    .build();
```

**Beneficio:** el servicio de chat recibe una política completa y válida. La configuración mutable del formulario no puede cambiar la política mientras se procesa un mensaje.

## Factory Method · acciones de moderación

**Problema:** permitir, revisar, ocultar y silenciar requieren efectos diferentes en varias tablas.

**Implementación:** `patterns/ModerationActionCreator.java`. El creador abstracto define `createAction()` y el algoritmo común `execute(Context)`. Los creadores concretos instancian `AllowAction`, `ReviewAction`, `HideAction` o `MuteAction`. `ChatService.send()` selecciona el creador según la clasificación y la política; el producto aplica sus efectos en la transacción final.

```text
ModerationActionCreator.execute(context)
    └─ createAction()              ← método fábrica redefinido
         ├─ AllowAction            → VISIBLE
         ├─ ReviewAction           → REVIEW + cola
         ├─ HideAction             → HIDDEN + historial + advertencia + cola
         └─ MuteAction             → ocultar + sanción temporal
```

**Beneficio:** los detalles SQL de cada acción quedan fuera de la selección de política. Se puede agregar un creador de otra acción sin modificar el algoritmo `execute`.

## Abstract Factory · familias de servicios inteligentes

**Problema:** moderación y asistencia editorial deben operar con una misma familia de capacidades. Sin clave de IA hay que seguir ejecutando reglas explícitas y comunicar el alcance disponible.

**Implementación:** `patterns/AiToolkitFactory.java`. La fábrica ofrece dos tipos de producto: `ModerationAnalyzer` y `EditorialAssistant`. `OllamaToolkit` crea ambos productos respaldados por el modelo local; `GeminiToolkit` es una familia alternativa; `LocalToolkit` crea productos de reglas y metadatos locales, claramente identificados. `AiService.toolkit()` selecciona la familia con `AI_PROVIDER`.

```text
AiToolkitFactory
  ├─ moderation() → ModerationAnalyzer
  └─ editorial()  → EditorialAssistant

OllamaToolkit: clasificación semántica + composición editorial local por ngrok
GeminiToolkit: alternativa con API externa
LocalToolkit:  restricciones explícitas + salida local identificada
```

**Beneficio:** el flujo consumidor usa contratos uniformes. La familia local no pretende ser una IA ni generar análisis semántico.

## Adapter · integración de Ollama por ngrok

**Problema:** el dominio no debe depender del formato REST específico del proveedor.

**Implementación:** `ai/OllamaAdapter.java` implementa `AiGateway.generate(instruction,input,schema)`. Envía el contrato con HTTPS y Bearer a la pasarela Java local, que convierte el esquema al JSON Schema de Ollama, fija el modelo y usa `/api/chat` con salida estructurada. El adaptador valida modelo y envoltura; los productos de `LlmToolkit` validan categoría, confianza y campos editoriales. `LocalAiGateway` se ejecuta como proceso separado del backend de Render. `GeminiAdapter` conserva una integración alternativa con el mismo contrato.

```text
AiToolkitFactory.OllamaToolkit
        ↓ contrato interno
AiGateway
        ↑ implementado por
OllamaAdapter → HTTPS ngrok → LocalAiGateway → Ollama /api/chat → JSON
```

**Beneficio:** un cambio del proveedor se concentra en el adaptador. El frontend nunca conoce la clave. Una respuesta inválida no puede convertirse directamente en una sanción.

## Bridge · tipos de aviso y mecanismos de entrega

**Problema:** los avisos de moderación y de clips necesitan persistencia y entrega en vivo. Heredar una clase por cada combinación haría crecer el número de clases innecesariamente.

**Implementación:** `patterns/NotificationBridge.java`. La jerarquía de abstracción es `Notice → ModerationNotice / ClipNotice`; la de implementación es `Delivery → DatabaseDelivery / RealtimeDelivery`. Una `Notice` tiene una referencia a `Delivery` y delega el envío. `RealtimeDelivery` persiste y emite por `LiveHub`. `ChatService` y `ClipService` usan las abstracciones.

```java
NotificationBridge.Delivery delivery = realtimeDelivery;
new NotificationBridge.ModerationNotice(delivery)
    .send(userId, "Tu mensaje requiere revisión humana");
new NotificationBridge.ClipNotice(delivery)
    .send(creatorId, "Tu clip está listo para revisar");
```

**Beneficio:** agregar otro tipo de aviso y agregar otro mecanismo de entrega son cambios independientes. La prueba del puente usa una entrega registradora sin depender de WebSocket ni SQL.

## Patrones adicionales investigados

**Strategy:** `ModerationAnalyzer` y `EditorialAssistant` son contratos de algoritmos sustituibles. La Abstract Factory crea familias de estos algoritmos; su ejecución tiene estructura de Strategy. **Observer / publicación-suscripción:** las conexiones suscritas a una sala reciben eventos de chat, presencia y subtítulos desde `LiveHub`; esto evita que cada espectador consulte continuamente los mismos cambios. Se aplica mediante WebSocket y suscripciones de sala, no mediante `java.util.Observable`.

**Separación por capas y servicio de aplicación:** los controladores validan el contrato HTTP, los servicios coordinan casos de uso, el acceso SQL usa `Db` y los adaptadores encapsulan sistemas externos. Estos patrones arquitectónicos complementan los cinco patrones GoF exigidos.

## Cómo demostrarlo en la sustentación

1. Cambia umbrales y palabras desde Configuración; muestra la validación del Builder.
2. Envía mensajes permitidos y restringidos; sigue la selección del creador y la aplicación de Factory Method.
3. Muestra los dos productos de cada familia de Abstract Factory y el indicador del proveedor.
4. Explica el JSON externo y la conversión en `OllamaAdapter` y `LocalAiGateway`; muestra el historial de la solicitud.
5. Genera un aviso de moderación y otro de clip; señala cómo ambos usan la misma entrega a través del Bridge.
