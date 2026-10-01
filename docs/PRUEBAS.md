# Verificación de StreamGuard

Verificación local del 1 de octubre de 2026. Se usaron Java 21, Maven 3.9.9, PostgreSQL 18 local, FFmpeg y Chrome mediante Playwright. Docker y CI usan PostgreSQL 17, compatible con la versión de Flyway incluida.

## Backend y patrones

Las **9 pruebas Java pasaron**, sin errores ni pruebas omitidas: cinco en `PatternsTest` y cuatro en `PlatformIntegrationTest`. Las pruebas de integración usan una base separada y solicitudes HTTP reales contra Spring Boot. Comprueban registro y sesiones, propiedad y permisos, configuración y moderación, decisiones humanas, persistencia, archivos multimedia, publicación y recorte real de clips con FFmpeg. Las pruebas de patrones verifican la construcción de políticas, clasificación local, validación de respuestas de la familia Gemini y delegación de avisos.

Las dos migraciones Flyway fueron aplicadas y validadas. El modelo contiene **64 tablas de dominio**, además de la tabla técnica de Flyway, y **101 referencias de clave foránea** en V1. El catálogo y el diagrama se derivan de la migración mediante `scripts/document-schema.py`.

El empaquetado del backend y la compilación del frontend Java GWT terminaron correctamente. En Windows se debe cerrar el proceso que ejecuta el JAR antes de volver a empaquetarlo: Java mantiene el archivo abierto.

## Navegador

`scripts/browser-test.cjs` ejecuta un recorrido con cámara y micrófono sintéticos y dos contextos independientes de Chrome. El informe está en `artifacts/browser-test-results.json`. Comprueba:

1. Carga del frontend compilado desde Java.
2. Registro y creación de un canal mediante formularios.
3. Cámara del creador y señalización del directo.
4. Recepción de video WebRTC reproducible en un segundo navegador.
5. Envío, persistencia y difusión de un mensaje del chat.
6. Restricción de un mensaje y publicación posterior por aprobación humana.
7. Captura y reproducción de un clip real, con publicación por el creador.
8. Navegación a 390 píxeles de ancho, sin desbordamiento horizontal.
9. Finalización del directo y aviso a los espectadores.

El recorrido exige que no ocurran errores JavaScript en las páginas. Las capturas desktop y móvil de `artifacts/` muestran la aplicación que ejecutaron las pruebas. La presentación se exportó y se revisaron sus 14 diapositivas renderizadas, además de las comprobaciones de estructura del PPTX.

## Repetir la verificación

Sigue [INICIO_LOCAL.md](INICIO_LOCAL.md) para crear la base de pruebas, configurar FFmpeg y ejecutar Maven y Playwright. El flujo de GitHub Actions también compila el proyecto y ejecuta las pruebas del backend contra PostgreSQL; su ejecución en GitHub debe comprobarse después de subir el código.

## Comprobaciones pendientes en las cuentas del propietario

La llamada a Gemini requiere tu clave y cuota y no se verificó contra una cuenta real. Se probaron las reglas locales y la validación de respuestas de los productos de la fábrica Gemini. Después de configurar `GEMINI_API_KEY`, comprueba moderación y generación editorial con la guía de despliegue.

El proyecto aún no está publicado en Vercel ni Railway, según tu indicación de hacerlo después. Los dominios HTTPS, las variables, el volumen persistente y el acceso entre redes con TURN requieren verificación después de desplegar. La prueba móvil usa Chrome con un tamaño de pantalla móvil; no sustituye una prueba física en todos los modelos de teléfono. El alcance de escalabilidad y compatibilidad está detallado en [README.md](../README.md).
