# Verificación de StreamGuard

Verificación local del 1 de octubre de 2026 tras migrar el frontend a TypeScript. Se usaron Node 24.11.1, TypeScript 7.0.2, Vite 8.3.2, Java 21, Maven 3.9.9, PostgreSQL 18 local, FFmpeg y Chrome mediante Playwright. Docker y CI usan PostgreSQL 17, compatible con la versión de Flyway incluida.

## Backend y patrones

Las **10 pruebas Java pasaron**, sin errores ni pruebas omitidas: cinco en `PatternsTest` y cinco en `PlatformIntegrationTest`. Las pruebas de integración usan una base separada y solicitudes HTTP reales contra Spring Boot. Comprueban registro y sesiones, propiedad y permisos, configuración y moderación, decisiones humanas, persistencia, archivos multimedia, publicación y recorte real de clips con FFmpeg. Las pruebas de patrones verifican la construcción de políticas, clasificación local, validación de respuestas de la familia Gemini y delegación de avisos.

Las dos migraciones Flyway fueron aplicadas y validadas. El modelo contiene **64 tablas de dominio**, además de la tabla técnica de Flyway, y **101 referencias de clave foránea** en V1. El catálogo y el diagrama se derivan de la migración mediante `scripts/document-schema.py`.

El backend conserva su JAR Java. Tras la migración se repitieron sus diez pruebas. `npm run build` comprueba TypeScript estricto de la interfaz y del service worker, valida los catálogos y compila con Vite. En Windows se debe cerrar el proceso que ejecuta el JAR antes de volver a empaquetarlo: Java mantiene el archivo abierto.

La prueba adicional comprueba que la validación devuelve los nombres y mensajes en español. El verificador de catálogos comprobó 578 referencias de traducción. Véase [IDIOMAS.md](IDIOMAS.md).

## Navegador

`scripts/browser-test.cjs` ejecuta un recorrido con cámara y micrófono sintéticos y dos contextos independientes de Chrome. El informe está en `artifacts/browser-test-results.json`. Comprueba:

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

También se comprobaron el título de la página y la traducción de un error de permisos de cámara. El recorrido exige que no ocurran errores JavaScript en las páginas. Las capturas desktop y móvil de `artifacts/` muestran la aplicación que ejecutaron las pruebas. La presentación se exportó y se revisaron sus 14 diapositivas renderizadas, además de las comprobaciones de estructura del PPTX.

## Repetir la verificación

Sigue [INICIO_LOCAL.md](INICIO_LOCAL.md) para crear la base de pruebas, configurar FFmpeg y ejecutar Maven y Playwright. El flujo de GitHub Actions también compila el proyecto y ejecuta las pruebas del backend contra PostgreSQL; su ejecución en GitHub debe comprobarse después de subir el código.

## Comprobaciones pendientes en las cuentas del propietario

La llamada a Gemini requiere tu clave y cuota y no se verificó contra una cuenta real. Se probaron las reglas locales y la validación de respuestas de los productos de la fábrica Gemini. Después de configurar `GEMINI_API_KEY`, comprueba moderación y generación editorial con la guía de despliegue.

El proyecto aún no está publicado en Vercel ni Railway, según tu indicación de hacerlo después. Los dominios HTTPS, las variables, el volumen persistente y el acceso entre redes con TURN requieren verificación después de desplegar. La prueba móvil usa Chrome con un tamaño de pantalla móvil; no sustituye una prueba física en todos los modelos de teléfono. El alcance de escalabilidad y compatibilidad está detallado en [README.md](../README.md).
