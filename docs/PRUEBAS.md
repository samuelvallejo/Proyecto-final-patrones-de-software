# Verificación de StreamGuard

Verificación del 2 de octubre de 2026 tras preparar el despliegue gratuito. Se usaron Node 24.11.1, TypeScript 7.0.2, Vite 8.3.2, Java 21, Maven 3.9.9, PostgreSQL 18 local, FFmpeg y Chrome mediante Playwright. Docker y CI usan PostgreSQL 17, compatible con la versión de Flyway incluida.

## Backend y patrones

Las **10 pruebas Java pasaron**, sin errores ni pruebas omitidas: cinco en `PatternsTest` y cinco en `PlatformIntegrationTest`. Las pruebas de integración usan una base separada y solicitudes HTTP reales contra Spring Boot. Comprueban registro y sesiones, propiedad y permisos, configuración y moderación, decisiones humanas, persistencia, archivos multimedia, publicación y recorte real de clips con FFmpeg. Las pruebas de patrones verifican la construcción de políticas, clasificación local, validación de respuestas de la familia Gemini y delegación de avisos.

Las tres migraciones Flyway fueron aplicadas y validadas. El modelo contiene **65 tablas de dominio**, además de la tabla técnica de Flyway, y **102 referencias de clave foránea**. El catálogo y el diagrama se derivan de la migración mediante `scripts/document-schema.py`.

El backend conserva su JAR Java. Tras la migración se repitieron sus diez pruebas. `npm run build` comprueba TypeScript estricto de la interfaz y del service worker, valida los catálogos y compila con Vite. En Windows se debe cerrar el proceso que ejecuta el JAR antes de volver a empaquetarlo: Java mantiene el archivo abierto.

La prueba adicional comprueba que la validación devuelve los nombres y mensajes en español. El verificador de catálogos comprobó 582 referencias de traducción. Véase [IDIOMAS.md](IDIOMAS.md).

Se verificó además el arranque del backend local contra PostgreSQL 17.11 de Supabase con SSL, rol privado, baseline V2 y migración V3. La prueba de clips borra la copia temporal y exige que la descarga reconstruida desde PostgreSQL tenga exactamente los mismos bytes. Los asesores de seguridad de Supabase no devolvieron hallazgos.

## Navegador

`scripts/browser-test.cjs` ejecuta un recorrido con cámara y micrófono sintéticos y dos contextos independientes de Chrome. El informe está en `artifacts/browser-test-results.json`. El recorrido pasó con el backend local conectado a Supabase, sin errores JavaScript. Comprueba:

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
12. Análisis del asistente con reglas locales y diálogo nativo de avisos.
13. Cierre/inicio de sesión y recuperación de la sesión después de recargar.

14. Reconexión del WebSocket, conservación de la cámara y recepción de nuevas pistas de video tras cerrar la conexión de señalización.

También se comprobaron el título de la página y la traducción de un error de permisos de cámara. El recorrido exige que no ocurran errores JavaScript en las páginas. Las capturas desktop y móvil de `artifacts/` muestran la aplicación que ejecutaron las pruebas. La presentación se exportó y se revisaron sus 14 diapositivas renderizadas, además de las comprobaciones de estructura del PPTX.

## Repetir la verificación

Sigue [INICIO_LOCAL.md](INICIO_LOCAL.md) para crear la base de pruebas, configurar FFmpeg y ejecutar Maven y Playwright. El flujo de GitHub Actions también compila el proyecto y ejecuta las pruebas del backend contra PostgreSQL; su ejecución en GitHub debe comprobarse después de subir el código.

## Comprobaciones pendientes en las cuentas del propietario

La llamada a Gemini requiere tu clave y cuota y no se verificó contra una cuenta real. Se probaron las reglas locales y la validación de respuestas de los productos de la fábrica Gemini. Después de configurar `GEMINI_API_KEY`, comprueba moderación y generación editorial con la guía de despliegue.

Los dominios públicos de Vercel y Render se verifican después del despliegue; una prueba con backend local y base remota no demuestra el funcionamiento del alojamiento público. El acceso entre redes con TURN también requiere configuración y verificación. La prueba móvil usa Chrome con un tamaño de pantalla móvil; no sustituye una prueba física en todos los modelos de teléfono. El alcance de escalabilidad y compatibilidad está detallado en [README.md](../README.md).
