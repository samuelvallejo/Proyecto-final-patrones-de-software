# Verificación de StreamGuard

Verificación del 2 de octubre de 2026 del despliegue público gratuito y la integración de Ollama por ngrok. Se usaron Node 24.11.1, TypeScript 7.0.2, Vite 8.3.2, Java 21, Maven 3.9.9, PostgreSQL 18 local, FFmpeg y Chrome mediante Playwright. La nube y CI usan PostgreSQL 17, compatible con la versión de Flyway incluida.

## Backend y patrones

Las **15 pruebas Java pasaron**, sin errores ni pruebas omitidas: seis en `PatternsTest`, cuatro en `LocalAiGatewayTest` y cinco en `PlatformIntegrationTest`. Las pruebas de integración usan una base separada y solicitudes HTTP reales contra Spring Boot. Comprueban registro y sesiones, propiedad y permisos, configuración y moderación, decisiones humanas, persistencia, archivos multimedia, publicación y recorte real de clips con FFmpeg. Las pruebas de patrones verifican la construcción de políticas, clasificación local, validación de respuestas de las familias y delegación de avisos. La pasarela se prueba con un proveedor controlado: transformación del esquema, autenticación obligatoria, rechazo de endpoints administrativos, límites de entrada, modelo incorrecto y respuestas inválidas. Una prueba de red corta la primera conexión y exige recuperación; una respuesta HTTP 401 no se reintenta.

Las cinco migraciones Flyway fueron aplicadas y validadas. El modelo contiene **65 tablas de dominio**, además de la tabla técnica de Flyway, y **102 referencias de clave foránea**. El catálogo y el diagrama se derivan de las migraciones mediante `scripts/document-schema.py`.

El backend conserva su JAR Java. `npm run build` comprueba TypeScript estricto de la interfaz y del service worker, valida los catálogos y compila con Vite. En Windows se debe cerrar el proceso que ejecuta el JAR antes de volver a empaquetarlo: Java mantiene el archivo abierto. La pasarela ejecuta una copia privada del JAR para permitir reconstruir el backend.

La prueba adicional comprueba que la validación devuelve los nombres y mensajes en español. El verificador de catálogos comprobó 587 referencias de traducción. Véase [IDIOMAS.md](IDIOMAS.md).

Se verificó además el arranque del backend público en Render contra PostgreSQL 17.11 de Supabase con SSL, rol privado, baseline V2 y migraciones hasta V5. La prueba de clips borra la copia temporal y exige que la descarga reconstruida desde PostgreSQL tenga exactamente los mismos bytes. Los asesores de seguridad de Supabase no devolvieron hallazgos.

## Navegador

`scripts/browser-test.cjs` ejecuta un recorrido con cámara y micrófono sintéticos y dos contextos independientes de Chrome. El informe está en `artifacts/browser-test-results.json`. Las **14 comprobaciones pasaron desde https://streamguard-delta.vercel.app**, con backend en Render, PostgreSQL en Supabase y Ollama real por ngrok, sin errores JavaScript. Comprueba:

1. Carga del frontend compilado desde TypeScript.
2. Registro y creación de un canal mediante formularios.
3. Cámara del creador y señalización del directo.
4. Recepción de video WebRTC reproducible en un segundo navegador.
5. Envío, persistencia y difusión de un mensaje del chat.
6. Restricción de un mensaje y publicación posterior por aprobación humana.
7. Captura y reproducción de un clip real, con publicación por el creador.
8. Edición del clip con recorte real y nueva aprobación obligatoria.
9. Descarga del archivo y reproducción del enlace público sin sesión.
10. Navegación a 390 píxeles de ancho, sin desbordamiento horizontal.
11. Finalización del directo y aviso a los espectadores.
12. Análisis del asistente con el modelo real y diálogo nativo de avisos.
13. Cierre/inicio de sesión y recuperación de la sesión después de recargar.

14. Reconexión del WebSocket, conservación de la cámara y recepción de nuevas pistas de video tras cerrar la conexión de señalización.

También se comprobaron el título de la página y la traducción de un error de permisos de cámara. El recorrido exige que no ocurran errores JavaScript en las páginas. Las capturas desktop y móvil de `artifacts/` muestran la aplicación que ejecutaron las pruebas. La presentación se exportó y se revisaron sus 14 diapositivas renderizadas, además de las comprobaciones de estructura del PPTX.

## Repetir la verificación

Sigue [INICIO_LOCAL.md](INICIO_LOCAL.md) para crear la base de pruebas, configurar FFmpeg y ejecutar Maven y Playwright. El flujo de GitHub Actions también compila el proyecto y ejecuta las pruebas del backend contra PostgreSQL. Para probar la web publicada: `$env:TEST_WEB_URL='https://streamguard-delta.vercel.app'; npm run test:e2e`. Este recorrido crea cuentas y contenido sintéticos en la base del entorno elegido.

## IA real y servicios públicos

`artifacts/local-ai-results.json` registra cuatro clasificaciones reales de Qwen3:4b-instruct mediante Ollama y ngrok: saludo permitido, amenaza explícita bloqueada, combate ficticio de Resident Evil permitido e intento de manipular las instrucciones que contiene una amenaza bloqueado. También incluye salida editorial en español. `PatternsTest` exige que las preguntas frecuentes procedan de preguntas repetidas del contexto; descarta preguntas inventadas. Estos casos acotados no representan una evaluación completa de precisión del modelo.

`artifacts/cloud-ai-results.json` conserva las solicitudes sintéticas del recorrido público: moderación y dos generaciones editoriales con estado `SUCCEEDED` y modelo `qwen3:4b-instruct`, además de la restricción aplicada por reglas locales. Las latencias registradas fueron 2,4 segundos para moderación y entre 9 y 12,2 segundos para asistencia editorial. Se comprobaron respuestas HTTP 200 del frontend en Vercel y en ambos dominios Firebase, y CORS hacia la API de Render para los tres orígenes.

Se observaron cierres de transporte antes de recibir cabeceras HTTP desde Render. Después de volver a desplegar se verificó la conexión real y el recorrido completo. El adaptador usa HTTP/1.1 y permite un solo reintento de ese tipo de fallo dentro del presupuesto original de 50 segundos; no reintenta HTTP de error, respuestas inválidas ni tiempos agotados. No se atribuye una causa definitiva al cierre inicial.

## Límites de la verificación

Gemini permanece como alternativa y no se verificó con una clave real: esta instalación usa Ollama. TURN no está configurado; la ruta alternativa de video utiliza el backend por WSS cuando la conexión WebRTC falla. La prueba móvil usa Chrome con un tamaño de pantalla móvil; no sustituye una prueba física en todos los modelos de teléfono. El computador del modelo debe permanecer despierto y conectado. El alcance de escalabilidad y compatibilidad está detallado en [README.md](../README.md).

## Retransmisión entre redes restrictivas, 3 de octubre de 2026

Se añadieron cuatro pruebas de `MediaRelayTest` para verificar que solo el propietario puede publicar, que un invitado puede recibir fragmentos, que se rechazan publicaciones de espectadores y fragmentos superiores a 1 MiB, que un directo finalizado deja de admitir video y que se respeta el límite de espectadores y la reconexión del propietario. Las 19 pruebas Java pasaron sin errores ni omisiones.

`node scripts/relay-test.cjs` bloquea WebRTC deliberadamente (`iceTransportPolicy: relay`, sin servidores ICE) y exige video decodificado y en movimiento en un navegador invitado. Comprueba continuidad durante varios fragmentos, sonido activable, reconexión del emisor, entrada de un espectador posterior y finalización del directo. `TEST_CAPTURE=screen` usa una pantalla animada sintética con audio del sistema y micrófono sintéticos; exige que la mezcla tenga una sola pista de audio. Los resultados se guardan en `artifacts/relay-test-results.json` y la captura en `artifacts/relay-playback.png`. Para probar la versión publicada, configura `TEST_WEB_URL` con Vercel o Firebase y `TEST_API_URL` con Render. Este recorrido crea una cuenta y un canal de prueba, y finaliza su directo al terminar. La verificación evalúa la ruta real por el backend, sin depender de conectividad directa entre los navegadores.
