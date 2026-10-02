# StreamGuard · En vivo, con confianza

Proyecto académico de streaming con frontend **TypeScript estricto / Vite**, backend **Java 21 / Spring Boot**, PostgreSQL con **65 tablas de dominio**, e IA local **Ollama / Qwen3:4b** conectada por **ngrok** mediante una pasarela Java autenticada. Interfaz PWA en español adaptable a computador y celular. Configuración gratuita: frontend en **Vercel y Firebase Hosting**, backend en **Render Free** y PostgreSQL en **Supabase**.

## Comienza aquí

1. Lee [INICIO_LOCAL.md](docs/INICIO_LOCAL.md) para ejecutar la aplicación.
2. Sigue [DESPLIEGUE.md](docs/DESPLIEGUE.md) para publicarla en tus cuentas.
3. Consulta [PATRONES.md](docs/PATRONES.md) para sustentar los cinco patrones requeridos.
4. Consulta [BASE_DE_DATOS.md](docs/BASE_DE_DATOS.md), [ARQUITECTURA.md](docs/ARQUITECTURA.md) y [REQUISITOS.md](docs/REQUISITOS.md).
5. Revisa [PRUEBAS.md](docs/PRUEBAS.md) y la [presentación editable](artifacts/StreamGuard-typescript-presentation.pptx), con su [guion de sustentación](docs/SUSTENTACION.md).
6. Consulta [IDIOMAS.md](docs/IDIOMAS.md): código en inglés y textos de la aplicación en recursos de español.
7. Consulta [IA_LOCAL.md](docs/IA_LOCAL.md) para iniciar el modelo y el túnel después de reiniciar el computador.

## Funciones implementadas

- Registro con consentimiento para el análisis del chat, inicio y cierre de sesión, contraseñas BCrypt y sesiones con tokens opacos almacenados como hash.
- Creación de canales, categorías, seguidores e invitación de moderadores existentes.
- Transmisión de cámara o pantalla con audio mediante WebRTC y señalización WebSocket autenticada. Visualización de directos y chat persistente en tiempo real.
- Moderación con reglas locales y clasificación semántica de Ollama; palabras/temas restringidos, enlaces, repetición, modo lento, advertencias y silencios temporales.
- Cola humana para casos ambiguos y mensajes ocultos, aprobación/rechazo, sanciones y revocación.
- Marcadores manuales, detección de aumentos del chat y del nivel del audio. Captura real de segmentos recientes con MediaRecorder, validación y procesamiento con FFmpeg.
- Revisión de clips, edición de títulos/descripciones, recorte de video, descarga y enlaces para compartir clips aprobados.
- Resúmenes, títulos y descripciones con el modelo local a partir del contexto registrado, temas, preguntas frecuentes y transcripciones del navegador cuando su API de voz está disponible.
- Panel con estadísticas obtenidas de PostgreSQL y muestras de audiencia por minuto; notificaciones persistentes y en tiempo real.

## Cinco patrones principales

| Patrón | Código | Uso en el flujo real |
|---|---|---|
| Builder | `ModerationPolicy.Builder` | Construye y valida las reglas de cada canal. |
| Factory Method | `ModerationActionCreator` | Crea acciones de permitir, revisar, ocultar y silenciar. |
| Abstract Factory | `AiToolkitFactory` | Selecciona familias compatibles de moderación y asistencia editorial. |
| Adapter | `OllamaAdapter` | Traduce el contrato interno al acceso autenticado al modelo por ngrok. `GeminiAdapter` es una alternativa opcional. |
| Bridge | `NotificationBridge` | Separa tipos de aviso de su persistencia y entrega en tiempo real. |

## Estructura

```text
backend/                     API Java, seguridad, WebSocket, IA y pruebas
  src/main/resources/db/     Migraciones PostgreSQL V1, V2 y V3
frontend/src/                Interfaz, API, WebRTC y PWA en TypeScript
frontend/public/             Catálogo en español, iconos y manifest
frontend/index.html          Entrada HTML compilada por Vite
frontend/tsconfig*.json      Tipado estricto de interfaz y service worker
scripts/                    Compilación, servidor local y pruebas de navegador
docs/                       Guías, arquitectura, requisitos y sustentación
Dockerfile                  Backend Java y FFmpeg
render.yaml                 Servicio Render Free y variables requeridas
railway.toml                Configuración alternativa de Railway
vercel.json                 Compilación y publicación del frontend
compose.yml                 PostgreSQL y backend para desarrollo con Docker
.github/workflows/ci.yml     Compilación y pruebas automatizadas
```

## Alcance de esta versión

Es una implementación funcional para demostración académica y comunidades pequeñas. WebRTC distribuye una conexión desde el creador a cada espectador: el límite predeterminado es **6 espectadores por directo**. Se despliega **una réplica** del backend. En el perfil cloud, PostgreSQL conserva los clips y el disco del backend sirve como copia temporal. Render Free se suspende después de periodos sin tráfico y el primer acceso puede tardar en despertar el servicio. Los clips y las tablas comparten la cuota gratuita de Supabase. Para audiencias masivas se necesita un SFU o servicio multimedia, CDN, almacenamiento de objetos y distribución de eventos entre réplicas.

Los clips capturan el segmento actual o el segmento reciente de hasta aproximadamente **15 segundos**; no se guarda automáticamente todo el directo. La detección de audio mide energía, no interpreta escenas. Los resúmenes analizan chat, marcadores y transcripción disponible, no el video completo. La transcripción depende de la API de voz del navegador y de permisos del micrófono. Los clips se generan como WebM; los enlaces de compartir abren el clip dentro de la plataforma.

El computador del modelo debe permanecer encendido y conectado a ngrok. Una respuesta fallida o inválida pasa a revisión humana. Las reglas explícitas siguen funcionando aunque el modelo esté apagado. `AI_PROVIDER=local-rules` permite trabajar sin modelo y no simula análisis de IA. Para conexiones entre redes que no permitan WebRTC directo, configura **TURN**.

El modelo incluye tablas para futuras extensiones (suscripciones, insignias, emotes, listas, webhooks y apelaciones). No se incluyen cobros ni entrega externa de webhooks en esta versión. Ver la separación exacta entre funciones operativas y extensiones en la documentación de la base de datos.

## Referencias oficiales

Vite compila TypeScript y prepara los archivos estáticos para el navegador: [Vite](https://vite.dev/guide/). El proyecto comprueba tipos antes de compilar: [TypeScript estricto](https://www.typescriptlang.org/tsconfig/strict.html). Vercel sirve la salida `frontend/dist`: [configuración de compilaciones](https://vercel.com/docs/builds/configure-a-build). Render despliega Docker: [servicios web](https://render.com/docs/web-services), [límites del plan gratuito](https://render.com/docs/free). Supabase proporciona PostgreSQL: [Spring Boot y pooler](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot). La clave de Gemini se usa exclusivamente desde el backend: [claves de API](https://ai.google.dev/gemini-api/docs/api-key).
