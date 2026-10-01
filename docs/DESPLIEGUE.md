# Publicación en Railway y Vercel

El código queda preparado para el despliegue. Las cuentas, proyectos y claves se configuran por el propietario. El orden recomendado es **PostgreSQL → backend Railway → frontend Vercel → origen permitido**.

## 1. Repositorio

Usa [Proyecto-final-patrones-de-software](https://github.com/samuelvallejo/Proyecto-final-patrones-de-software), rama **`codex/streamguard`**. Selecciona esa rama al conectar los servicios. No subas `.env`, claves, `.tools`, `.local` ni bases de datos de pruebas.

## 2. PostgreSQL en Railway

1. Entra a [Railway](https://railway.com), crea un proyecto y agrega el servicio **PostgreSQL**.
2. Mantén la base y el backend en el mismo proyecto y entorno.
3. Identifica el nombre del servicio PostgreSQL. Los ejemplos siguientes usan **Postgres**; sustitúyelo si tu servicio tiene otro nombre.
4. No necesitas exponer PostgreSQL al frontend. El backend se conecta por la red privada.

La plataforma proporciona las variables de conexión del servicio PostgreSQL. [Documentación de PostgreSQL](https://docs.railway.com/databases/postgresql).

## 3. Backend Java en Railway

1. Agrega un servicio desde tu repositorio de GitHub. Usa la **raíz del repositorio (`/`)**, ya que el Dockerfile está allí.
2. Selecciona la rama con el proyecto. Railway usa `Dockerfile` y `railway.toml`.
3. En **Variables** del servicio backend, crea:

| Variable | Valor |
|---|---|
| `JDBC_DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `PGUSER` | `${{Postgres.PGUSER}}` |
| `PGPASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `FRONTEND_ORIGIN` | `https://TU-PROYECTO.vercel.app` (se ajusta al terminar Vercel) |
| `GEMINI_API_KEY` | Tu clave de Google AI Studio |
| `GEMINI_MODEL` | `gemini-2.5-flash`, o un modelo disponible compatible con salida estructurada |
| `MEDIA_DIR` | `/data/media` |
| `MAX_VIEWERS` | `6` |

4. Agrega un **volumen persistente al servicio backend**, montado en **`/data`**. El volumen de PostgreSQL no reemplaza este volumen: uno contiene tablas y el otro contiene archivos de video.
5. Mantén **una réplica del backend**, porque las conexiones WebSocket y salas se administran en memoria.
6. Despliega. Flyway creará las 64 tablas de dominio y su tabla técnica de migraciones.
7. En la configuración de red del backend, genera un dominio público HTTPS. Guarda la dirección, por ejemplo `https://streamguard-backend.up.railway.app`.
8. Abre `https://TU-BACKEND.up.railway.app/actuator/health`. Debe responder `UP`.

No establezcas un comando de inicio adicional: el Dockerfile inicia el JAR y Spring toma el puerto de `PORT`. FFmpeg se instala durante la construcción. [Spring Boot en Railway](https://docs.railway.com/guides/spring-boot), [variables de referencia](https://docs.railway.com/variables), [volúmenes](https://docs.railway.com/volumes).

## 4. Gemini

1. Entra a [Google AI Studio](https://aistudio.google.com) con tu cuenta y crea o selecciona una clave de API.
2. Configúrala como `GEMINI_API_KEY` **en el backend Railway**.
3. Vuelve a desplegar/reiniciar el backend para que lea las variables.
4. En el estudio debe aparecer **Gemini está conectado**. Esto confirma que existe configuración; la comprobación de una llamada real se hace enviando un mensaje o generando un resumen.
5. Si hay una cuota agotada, un modelo inválido o un error del proveedor, el chat se envía a revisión humana y el historial `ai_requests` registra `FAILED`. No se muestran claves ni errores internos al usuario.

La integración usa solicitudes REST desde Java y exige JSON estructurado, validado antes de aplicar decisiones. [Claves de Gemini](https://ai.google.dev/gemini-api/docs/api-key), [salidas estructuradas](https://ai.google.dev/gemini-api/docs/structured-output).

## 5. Frontend TypeScript en Vercel

1. Entra a [Vercel](https://vercel.com) y selecciona **Add New → Project**.
2. Importa el repositorio de GitHub y selecciona la rama del proyecto como rama de producción.
3. Framework: **Other**. Root Directory: **raíz del repositorio**. No selecciones `backend` ni `frontend` como raíz.
4. Añade una variable de entorno pública:

```text
PUBLIC_API_URL=https://TU-BACKEND.up.railway.app
```

Usa el origen, sin `/api`, rutas, claves o parámetros. Debe usar HTTPS.

5. `vercel.json` ya establece:

```text
Install Command: npm ci
Build Command: npm run build
Output Directory: frontend/dist
```

6. Selecciona Node.js **24.x** en la configuración de compilación y despliega. npm instala las versiones fijadas en `package-lock.json`; el script verifica TypeScript estricto y compila con Vite. La salida es HTML, CSS y JavaScript generado desde TypeScript. Vercel no necesita Java ni Maven.
7. Guarda el dominio definitivo de Vercel.

Vercel sirve los archivos generados; la aplicación Java del servidor permanece en Railway. `PUBLIC_API_URL` se incorpora a la compilación: al cambiarla, vuelve a desplegar el frontend. [Configurar una compilación](https://vercel.com/docs/builds/configure-a-build), [Vite](https://vite.dev/guide/).

## 6. Conectar los dominios

1. Regresa al backend Railway y establece `FRONTEND_ORIGIN` con el origen exacto de Vercel, por ejemplo `https://proyecto-final-patrones-de-software.vercel.app`.
2. Reinicia/despliega el backend.
3. Si necesitas un dominio personalizado o una URL de vista previa adicional, agrega los orígenes explícitos separados por coma, sin espacios ni barra final.
4. Prueba registro, creación de canal, mensajes y WebSocket desde Vercel.

## 7. Video entre redes: TURN

STUN se configura automáticamente. Si el video funciona en una red y falla entre redes distintas, usa un servidor TURN y establece en Railway:

```text
TURN_URL=turns:TU-SERVIDOR:5349
TURN_USERNAME=TU-USUARIO
TURN_PASSWORD=TU-CREDENCIAL
```

Las credenciales de conexión TURN se entregan al navegador, como requiere WebRTC; usa credenciales acotadas/temporales en un despliegue público. La señalización usa HTTPS/WSS; Vercel no aloja el servidor de WebSocket.

## 8. Comprobación final

- Salud del backend `UP`, migraciones completas y volumen montado.
- Registro e inicio de sesión desde Vercel.
- Un creador emitiendo y otro usuario viendo desde una ventana o dispositivo distinto.
- Chat visible en tiempo real y palabra restringida enviada a la cola de moderación.
- Decisión humana y silencio temporal efectivos.
- Marcador que produzca un archivo reproducible, edición y publicación.
- Resumen de Gemini con una llamada exitosa registrada.
- Vista móvil y posibilidad de agregar la PWA a inicio.

## Resolución de fallos habituales

| Síntoma | Qué revisar |
|---|---|
| El frontend no conecta | `PUBLIC_API_URL`, dominio HTTPS del backend y `FRONTEND_ORIGIN`. |
| Error de base de datos al iniciar | Nombre real del servicio Postgres, referencias `PGHOST/PGPORT/PGDATABASE`, usuario y contraseña. |
| Directo sin video | Emisor conectado, permisos de cámara, HTTPS, límite de espectadores y TURN. |
| Clips fallan o desaparecen tras reiniciar | FFmpeg instalado (Dockerfile), volumen backend en `/data` y `MEDIA_DIR=/data/media`. |
| IA muestra reglas locales | `GEMINI_API_KEY` ausente en backend o servicio aún sin reiniciar. |
| IA envía todos los mensajes a revisión | Cuota, modelo, conectividad o respuesta inválida. Revisa `ai_requests.status`. |
| La compilación del frontend falla | Node 24.x, `npm ci`, dependencias de `package-lock.json` y el resultado de `npm run typecheck`. |

Railway, almacenamiento y Gemini pueden requerir un plan o consumo facturado según la cuenta. Esta entrega no configura facturación ni publica automáticamente en tus cuentas.
