# Guion de sustentación

La presentación editable está en `artifacts/StreamGuard-typescript-presentation.pptx`. El código de autoría está en `scripts/build-presentation.mjs`. El diseño sigue los colores de la aplicación. Las capturas proceden de las pruebas del producto, incluida una cámara sintética; no se presentan como transmisiones de una persona real.

## Recorrido sugerido de 10 a 15 minutos

1. Explica por qué un chat de streaming necesita reglas automáticas y revisión humana.
2. Muestra la separación frontend/backend/base de datos/IA y la compilación de TypeScript con Vite para Vercel.
3. Describe el modelo relacional, verifica las 64 tablas y señala relaciones concretas del chat y de los clips.
4. Recorre los cinco patrones en el código y vincula cada uno con una acción de la aplicación.
5. Demuestra un directo con dos cuentas. Usa cámara sintética o una cámara propia con permisos.
6. Envía un mensaje permitido y otro con una palabra restringida. Apruébalo como moderador y muestra el cambio visible.
7. Marca un momento, reproduce el clip, recórtalo y publícalo. Abre el enlace público en otra ventana.
8. Con una clave válida, genera un análisis con Gemini y explica qué contexto recibe.
9. Muestra la vista móvil y explica cómo publicar los servicios siguiendo DESPLIEGUE.md.
10. Explica el alcance: audiencias pequeñas, segmentos breves, una réplica y necesidad de SFU/CDN para grandes audiencias.

## Preguntas técnicas esperables

**¿Qué lenguaje usa cada capa?** El frontend usa TypeScript estricto en `frontend/src/`, que Vite compila para el navegador. El backend usa Java 21 / Spring Boot. PostgreSQL conserva las 64 tablas y sus relaciones. Los cinco patrones principales se implementan en el backend Java.

**¿Por qué no se llama a Gemini desde el frontend?** El backend conserva la clave, aplica permisos y registra/valida la respuesta antes de ejecutar una acción.

**¿Factory Method y Abstract Factory son lo mismo?** Factory Method delega la creación de un producto a creadores concretos. Abstract Factory crea una familia de productos relacionados: moderación y asistencia editorial.

**¿Qué desacopla Bridge?** El tipo de aviso de la forma de entrega. Un aviso de clip o moderación puede usar persistencia o persistencia con difusión en vivo.

**¿Por qué tantas tablas?** El dominio separa identidades, permisos por canal, eventos, chat, decisiones, sanciones, IA, clips y versiones. El catálogo distingue tablas operativas, catálogos y extensiones previstas. La cantidad no reemplaza el análisis de relaciones y restricciones.

**¿Qué pasa cuando Gemini falla?** El mensaje pasa a revisión humana y la solicitud conserva el estado `FAILED`. El frontend no afirma que una regla local sea un análisis de IA.

**¿Cómo se protege un clip pendiente?** El endpoint de archivo comprueba propiedad o existencia de un clip aprobado. Editar un clip crea un archivo nuevo cuando hay recorte y vuelve a ponerlo en estado pendiente.

**¿Puede atender miles de espectadores?** Esta versión distribuye conexiones WebRTC directas y limita a seis espectadores. La arquitectura documenta la evolución a SFU, CDN, almacenamiento de objetos y eventos compartidos.
