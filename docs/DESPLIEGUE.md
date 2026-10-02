# Publicación gratuita: Supabase, Render y Vercel

Los visitantes acceden por Vercel y no instalan PostgreSQL, Java ni HeidiSQL. Render Free ejecuta el backend Java y Supabase conserva la base de datos. La rama del [repositorio](https://github.com/samuelvallejo/Proyecto-final-patrones-de-software) es `codex/streamguard`.

Direcciones publicadas: [Vercel](https://streamguard-delta.vercel.app), [Firebase Hosting](https://streamguard-samuel-261002.web.app), [salud del backend Render](https://streamguard-backend.onrender.com/actuator/health). Servicio Render: `srv-davtsdp42hec73e5edn0`. Proyecto Firebase: `streamguard-samuel-261002` (Spark, sin facturación).

## PostgreSQL y otros computadores

Proyecto [streamguard en Supabase](https://supabase.com/dashboard/project/jegiuidrxxyijutqoowd), región `us-east-1`, esquema privado `streamguard`. El backend conecta mediante el pooler de sesiones, SSL y un rol dedicado sin privilegios de superusuario. El frontend no recibe credenciales ni consulta las tablas directamente. Las tablas tienen RLS y no se exponen a las claves públicas de Supabase.

Un administrador puede usar HeidiSQL, DBeaver o pgAdmin con PostgreSQL y estos parámetros:

| Campo | Valor |
|---|---|
| Host | `aws-0-us-east-1.pooler.supabase.com` |
| Puerto | `5432` |
| Base | `postgres` |
| Usuario del backend | `streamguard_backend.jegiuidrxxyijutqoowd` |
| Contraseña | Credencial privada configurada en Render |
| SSL | Obligatorio |
| Esquema | `streamguard` |

Usa un rol de lectura separado para consultas administrativas frecuentes; no distribuyas el usuario del backend a los visitantes. [Roles PostgreSQL](https://supabase.com/docs/guides/database/postgres/roles), [Spring Boot y pooler](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot).

Se inicializó el snapshot exacto V1/V2. El perfil `cloud` reconoce esa base como versión 2 y Flyway aplica V3 y versiones posteriores. Hay **65 tablas de dominio y 102 referencias de clave foránea**, más `flyway_schema_history`. `scripts/prepare-supabase.mjs` genera un bootstrap exclusivamente para un esquema nuevo; no lo ejecutes sobre un proyecto ya utilizado. No edites migraciones aplicadas.

## Backend Java en Render

1. Crea un **Web Service** desde el repositorio público y selecciona `codex/streamguard`.
2. Nombre: `streamguard-backend`. Lenguaje: **Docker**. Raíz: repositorio completo. Dockerfile: `./Dockerfile`. Región: **Virginia**.
3. Selecciona explícitamente **Free, $0/month**; el formulario puede seleccionar inicialmente un plan de pago.
4. Health Check Path: `/actuator/health`. Mantén una sola instancia.
5. Configura las variables privadas siguientes. No subas contraseñas a Git ni al frontend.

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `cloud` |
| `JDBC_DATABASE_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| `PGUSER` | `streamguard_backend.jegiuidrxxyijutqoowd` |
| `PGPASSWORD` | Contraseña privada del rol |
| `FRONTEND_ORIGIN` | `https://streamguard-delta.vercel.app,https://streamguard-samuel-261002.web.app,https://streamguard-samuel-261002.firebaseapp.com` |
| `MEDIA_STORAGE` | `database` |
| `MEDIA_DIR` | `/tmp/streamguard/media` |
| `MEDIA_WORK_DIR` | `/tmp/streamguard/work` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=50 -XX:ActiveProcessorCount=1` |
| `MAX_VIEWERS` | `6` |
| `AI_PROVIDER` | `ollama` |
| `OLLAMA_GATEWAY_URL` | Origen HTTPS de ngrok |
| `OLLAMA_GATEWAY_TOKEN` | Clave privada de la pasarela local, distinta del authtoken de ngrok |
| `OLLAMA_MODEL` | `qwen3:4b-instruct` |

6. Despliega. Docker instala FFmpeg y construye el JAR Java 21. Spring escucha en `PORT`, asignado por Render.
7. Espera a que el servicio indique **Live** y `/actuator/health` responda `{"status":"UP"}`.
8. Copia el origen HTTPS definitivo de Render para `PUBLIC_API_URL` en Vercel; sin `/api`, rutas ni barra final.
9. Inicia la IA en el computador del propietario según [IA_LOCAL.md](IA_LOCAL.md). Después de subir cambios a la rama pública, usa **Manual Deploy → Deploy latest commit** si el repositorio no tiene un proveedor Git autorizado para despliegue automático.

`render.yaml` documenta la misma configuración para un Blueprint. Completa allí las variables con `sync: false`. El plan Free pierde los archivos locales al reiniciarse: `media_asset_contents` conserva los clips en PostgreSQL y FFmpeg usa archivos temporales. Cada clip admite hasta 30 MiB. Los clips comparten la cuota de la base; este almacenamiento está pensado para la demostración académica.

Render Free puede dormir tras 15 minutos sin tráfico y tardar alrededor de un minuto en despertar. Tiene límites de horas, tráfico y compilación. No se activan planes ni discos de pago. [Límites oficiales](https://render.com/docs/free), [servicios web](https://render.com/docs/web-services).

## Frontend TypeScript en Vercel

1. Usa el proyecto `streamguard`, conectado al repositorio, rama de producción `codex/streamguard`.
2. Framework: **Other**. Root Directory: **raíz del repositorio**. Node: **24.x**.
3. Configura `PUBLIC_API_URL` con el origen HTTPS real de Render.
4. `vercel.json` establece `npm ci`, `npm run build` y salida `frontend/dist`. El proceso valida traducciones y TypeScript estricto y compila la UI y el service worker.
5. Publica y abre `https://streamguard-delta.vercel.app`. Asignar un dominio no confirma un despliegue exitoso: verifica los flujos al final.

Vercel recibe solamente el origen público del backend. No añadas `PGPASSWORD` ni `GEMINI_API_KEY`. Si cambias `PUBLIC_API_URL`, vuelve a compilar. Si cambias el dominio frontend, ajusta `FRONTEND_ORIGIN` en Render al origen exacto; los orígenes adicionales se separan por comas. [Compilaciones Vercel](https://vercel.com/docs/builds/configure-a-build).

## IA local y conexiones entre redes

El proveedor de esta instalación es Ollama, con Qwen3:4b-instruct en el computador del propietario y ngrok como túnel. Configura las cuatro variables indicadas en [IA_LOCAL.md](IA_LOCAL.md). El authtoken de ngrok queda local; Render recibe solamente la URL y la clave independiente de la pasarela.

Configurar un proveedor no demuestra que una solicitud haya sido exitosa: envía un mensaje o genera un resumen y comprueba `ai_requests` y `ai_responses`. Los fallos del proveedor pasan a revisión humana. `AI_PROVIDER=local-rules` desactiva el modelo explícitamente; Gemini sigue disponible como adaptador alternativo, sin configurar en este despliegue.

WebRTC usa STUN, una conexión por espectador y un máximo de seis espectadores por directo. Algunas redes necesitan TURN: configura `TURN_URL`, `TURN_USERNAME` y `TURN_PASSWORD` si dispones de un servidor; no se ha contratado uno. Chat y señalización funcionan por WSS. Ante un corte breve el cliente reintenta y el backend espera 45 segundos antes de finalizar el directo.

## Firebase opcional

Render reemplaza el alojamiento Java de Google para respetar la decisión de no activar facturación. Firebase Hosting puede publicar una copia adicional estática en Spark con la misma API de Render. No se utilizan Cloud Run ni App Hosting. El propietario aceptó los términos de Google Cloud y Firebase y se publicó el sitio `streamguard-samuel-261002.web.app`. También sirve desde `streamguard-samuel-261002.firebaseapp.com`. Ambos orígenes están configurados en Render. Para actualizarlo: compila con `PUBLIC_API_URL=https://streamguard-backend.onrender.com` y ejecuta `npx -y firebase-tools@latest deploy --only hosting --project streamguard-samuel-261002`. [Planes de Firebase](https://firebase.google.com/pricing).

## Comprobación final

1. Backend `UP`, migraciones hasta V3 y PostgreSQL por SSL.
2. Registro, sesión y creación de canal desde Vercel.
3. Emisor y espectador recibiendo video y chat.
4. Mensaje restringido en cola y decisión humana.
5. Captura, recorte, publicación, descarga y enlace público de un clip.
6. Reproducción después de reiniciar el backend y reconexión después de un corte.
7. Navegación móvil sin desbordamiento.
8. Clasificación y resumen reales con Ollama, registrados como `SUCCEEDED`; revisión humana cuando el túnel no está disponible.

Si Render está despertando, espera a que salud responda y recarga. Ante errores revisa HTTPS, CORS y credenciales del rol. Para clips revisa FFmpeg, `MEDIA_STORAGE=database` y espacio en Supabase. No muestres secretos en capturas ni registros de soporte.
