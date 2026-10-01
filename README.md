# StreamGuard · Live, con confianza

Proyecto académico de streaming con frontend **escrito en Java (GWT)**, backend **Java 21 / Spring Boot**, PostgreSQL con **64 tablas de dominio**, y un adaptador real para la API de **Gemini**. Interfaz PWA adaptable a computador y celular. Configuración de despliegue: frontend en **Vercel**, backend y PostgreSQL en **Railway**.

## Comienza aquí

1. Lee [INICIO_LOCAL.md](docs/INICIO_LOCAL.md) para ejecutar la aplicación.
2. Sigue [DESPLIEGUE.md](docs/DESPLIEGUE.md) para publicarla en tus cuentas.
3. Consulta [PATRONES.md](docs/PATRONES.md) para sustentar los cinco patrones requeridos.
4. Consulta [BASE_DE_DATOS.md](docs/BASE_DE_DATOS.md), [ARQUITECTURA.md](docs/ARQUITECTURA.md) y [REQUISITOS.md](docs/REQUISITOS.md).
5. Revisa [PRUEBAS.md](docs/PRUEBAS.md) y la [presentación editable](artifacts/StreamGuard-presentacion-final.pptx), con su [guion de sustentación](docs/SUSTENTACION.md).

## Funciones implementadas

- Registro con consentimiento para el análisis del chat, inicio y cierre de sesión, contraseñas BCrypt y sesiones con tokens opacos almacenados como hash.
- Creación de canales, categorías, seguidores e invitación de moderadores existentes.
- Transmisión de cámara o pantalla con audio mediante WebRTC y señalización WebSocket autenticada. Visualización de directos y chat persistente en tiempo real.
- Moderación con reglas locales y clasificación semántica de Gemini; palabras/temas restringidos, enlaces, repetición, modo lento, advertencias y silencios temporales.
- Cola humana para casos ambiguos y mensajes ocultos, aprobación/rechazo, sanciones y revocación.
- Marcadores manuales, detección de aumentos del chat y del nivel del audio. Captura real de segmentos recientes con MediaRecorder, validación y procesamiento con FFmpeg.
- Revisión de clips, edición de títulos/descripciones, recorte de video, descarga y enlaces para compartir clips aprobados.
- Resúmenes, títulos y descripciones con Gemini a partir del contexto registrado, temas, preguntas frecuentes y transcripciones del navegador cuando su API de voz está disponible.
- Panel con estadísticas obtenidas de PostgreSQL y muestras de audiencia por minuto; notificaciones persistentes y en tiempo real.

## Cinco patrones principales

| Patrón | Código | Uso en el flujo real |
|---|---|---|
| Builder | `ModerationPolicy.Builder` | Construye y valida las reglas de cada canal. |
| Factory Method | `ModerationActionCreator` | Crea acciones de permitir, revisar, ocultar y silenciar. |
| Abstract Factory | `AiToolkitFactory` | Selecciona familias compatibles de moderación y asistencia editorial. |
| Adapter | `GeminiAdapter` | Traduce el contrato interno a solicitudes y respuestas de Gemini. |
| Bridge | `NotificationBridge` | Separa tipos de aviso de su persistencia y entrega en tiempo real. |

## Estructura

```text
backend/                     API Java, seguridad, WebSocket, IA y pruebas
  src/main/resources/db/     Migraciones PostgreSQL V1 y V2
frontend/src/main/java/      Aplicación Java GWT y adaptador de navegador
frontend/src/main/webapp/    HTML, CSS, PWA y APIs nativas de medios
scripts/                    Compilación, servidor local y pruebas de navegador
docs/                       Guías, arquitectura, requisitos y sustentación
Dockerfile                  Backend Java y FFmpeg para Railway
railway.toml                Salud y configuración del backend
vercel.json                 Compilación y publicación del frontend
compose.yml                 PostgreSQL y backend para desarrollo con Docker
.github/workflows/ci.yml     Compilación y pruebas automatizadas
```

## Alcance de esta versión

Es una implementación funcional para demostración académica y comunidades pequeñas. WebRTC distribuye una conexión desde el creador a cada espectador: el límite predeterminado es **6 espectadores por directo**. Se despliega **una réplica** del backend y se usa un volumen persistente para los videos. Para audiencias masivas se necesita un SFU o servicio multimedia, CDN, almacenamiento de objetos y distribución de eventos entre réplicas.

Los clips capturan el segmento actual o el segmento reciente de hasta aproximadamente **15 segundos**; no se guarda automáticamente todo el directo. La detección de audio mide energía, no interpreta escenas. Los resúmenes analizan chat, marcadores y transcripción disponible, no el video completo. La transcripción depende de la API de voz del navegador y de permisos del micrófono. Los clips se generan como WebM; los enlaces de compartir abren el clip dentro de la plataforma.

Sin `GEMINI_API_KEY`, la aplicación indica **Reglas locales**, conserva las restricciones explícitas y no simula análisis de IA. Con una clave configurada, una respuesta fallida o inválida pasa a revisión humana. Para conexiones entre redes que no permitan WebRTC directo, configura **TURN**.

El modelo incluye tablas para futuras extensiones (suscripciones, insignias, emotes, listas, webhooks y apelaciones). No se incluyen cobros ni entrega externa de webhooks en esta versión. Ver la separación exacta entre funciones operativas y extensiones en la documentación de la base de datos.

## Referencias oficiales

GWT permite escribir el cliente en Java y compilarlo a JavaScript que se ejecuta en navegadores, incluidos móviles: [GWT](https://www.gwtproject.org/overview.html). Los archivos generados se sirven como un frontend estático en Vercel: [configuración de compilaciones](https://vercel.com/docs/builds/configure-a-build). Railway permite desplegar Spring Boot mediante Docker: [guía oficial](https://docs.railway.com/guides/spring-boot). La clave de Gemini se usa exclusivamente desde el backend: [claves de API](https://ai.google.dev/gemini-api/docs/api-key).
