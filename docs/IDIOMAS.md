# Código en inglés e interfaz en español

Las clases, métodos, variables, claves JSON de la API, consultas, comentarios, instrucciones a Gemini y diagnósticos técnicos se escriben en inglés. Las instrucciones a Gemini exigen explícitamente que sus respuestas visibles se generen en español.

Los textos en español son **datos de traducción**, separados del código ejecutable:

| Recurso | Uso |
|---|---|
| `frontend/public/locales/es.json` | Pantallas, formularios, accesibilidad y mensajes de cámara/video. |
| `frontend/public/locales/noscript.es.html` | Ayuda cuando JavaScript está desactivado. |
| `backend/src/main/resources/locales/es.json` | Errores de API, decisiones, avisos y metadatos locales. |
| `backend/src/test/resources/fixtures/es.json` | Datos de prueba, incluidos casos de normalización de acentos. |
| `scripts/locales/browser.es.json` | Etiquetas y contenido de las pruebas de navegador. |
| `scripts/locales/schema.es.json` | Contenido de la documentación de PostgreSQL. |
| `scripts/locales/presentation.es.json` | Contenido y notas de la presentación. |

El frontend importa el catálogo JSON mediante `i18n.ts` y accede a él con `t(key)`. TypeScript comprueba las claves en compilación, incluyendo las traducciones de errores de medios. El backend usa `Messages.text(key)`, con un catálogo UTF-8 del classpath. Las claves de ambos catálogos se escriben en inglés. La ausencia de una clave produce un error de compilación o diagnóstico explícito; no se sustituye silenciosamente una traducción por texto en inglés.

La validación del backend devuelve nombres y explicaciones en español, independientemente del idioma del servidor. Por ejemplo, una dirección `Pasto` produce una explicación de las minúsculas permitidas en **Dirección del canal**, sin exponer el nombre interno `slug`. Los errores habituales de permisos de cámara y micrófono también se traducen al español.

Los nombres de categorías precargadas en V1 y el contenido almacenado por los usuarios son datos del producto en español. V1 y V2 no se modificaron: conservar sus checksums permite actualizar la aplicación sin alterar las bases existentes. La documentación académica y la presentación permanecen en español.

## Mantener esta separación

1. Escribe los nuevos identificadores y comentarios en inglés.
2. Agrega los textos visibles a los catálogos con una clave inglesa.
3. Usa `t`, `Messages.text` o el catálogo de datos del script correspondiente.
4. Ejecuta `node scripts/check-localization.mjs`, las pruebas Java y el recorrido de navegador.

El verificador comprueba las referencias explícitas y detecta caracteres españoles incrustados en archivos ejecutables; no sustituye una revisión humana de palabras sin acentos. Se ejecuta en GitHub Actions y antes de compilar para Vercel.
