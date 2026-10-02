# Trazabilidad del proyecto final

Las imágenes adjuntas se tomaron como requisitos académicos y explicación de arquitectura. El caso de estudio y las preferencias de PostgreSQL, Vercel y Render Free provienen de la solicitud del usuario. Las solicitudes posteriores cambian el frontend a TypeScript, conservan Java en el backend y sustituyen Gemini por Ollama local mediante ngrok.

| Requisito | Evidencia entregada |
|---|---|
| Caso de estudio de la vida real | Problemas de moderación y administración de directos; descritos en README y arquitectura. |
| Implementar IA | Adaptador Ollama, Qwen3:4b-instruct local por ngrok, clasificación de chat, asistencia editorial e historial. Gemini es una alternativa opcional. |
| Cinco patrones | Builder, Factory Method, Abstract Factory, Adapter y Bridge, con uso real y documentación. |
| Investigar otros patrones | Strategy, Observer/pub-sub y separación por capas en PATRONES.md. |
| Frontend, backend y BD | TypeScript / Vite, Java Spring Boot y PostgreSQL. |
| Java en backend | Código Java 21, JAR, Dockerfile y pruebas. |
| Frontend en TypeScript solicitado | Interfaz, comunicación HTTP, WebRTC, grabación y PWA en TypeScript estricto; compilación con Vite. |
| Aplicación en computador y celular | PWA, manifiesto, service worker y estilos adaptables. |
| Más de 45 tablas en PostgreSQL | 65 tablas de dominio verificadas mediante migraciones y prueba de integración. |
| Presentación del caso | Material de sustentación y presentación incluidos en docs/artifacts. |
| Producción en nube | Configuración para Vercel y Render Free y guía detallada. Publicación pendiente de que el usuario configure sus cuentas, según su indicación. |
| Moderación humana | Cola, decisiones, equipo por canal, sanciones y revocación. |
| Clips inteligentes | Marcadores, aumentos del chat/audio, captura real, edición, aprobación y descarga. |
| Funciones inteligentes complementarias | Resumen, metadatos, temas, preguntas y subtítulos según capacidades configuradas. |

Los grupos de hasta tres estudiantes son una condición de la entrega académica que debe completar el usuario. No se inventan integrantes ni se crean cuentas externas.
