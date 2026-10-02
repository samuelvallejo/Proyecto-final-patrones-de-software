# Ejecutar StreamGuard localmente

Necesitas **JDK 21** para el backend, **Node.js 24 LTS** para el frontend TypeScript y una base de datos **PostgreSQL 16 o superior**. Para crear y recortar clips necesitas `ffmpeg` y `ffprobe` en el PATH. En Windows puedes usar una compilación enlazada desde [FFmpeg](https://www.ffmpeg.org/download.html). `mvnw.cmd` y `mvnw` descargan Maven 3.9.9 dentro del proyecto si hace falta.

## Opción A: PostgreSQL y backend con Docker

En la raíz del proyecto:

```powershell
docker compose up --build -d
npm ci
npm run build
npm run preview
```

Abre [http://localhost:5173](http://localhost:5173). Docker instala FFmpeg en el contenedor del backend y mantiene PostgreSQL y archivos en volúmenes. Para la IA local consulta [IA_LOCAL.md](IA_LOCAL.md): inicia Ollama, la pasarela y ngrok y configura sus variables en el backend. Para pruebas sin modelo usa `AI_PROVIDER=local-rules`.

## Opción B: herramientas instaladas en Windows

1. Crea una base de datos `streamguard` y un usuario `streamguard` con contraseña propia en PostgreSQL.
2. En PowerShell, desde la raíz:

```powershell
$env:JDBC_DATABASE_URL='jdbc:postgresql://localhost:5432/streamguard'
$env:PGUSER='streamguard'
$env:PGPASSWORD='TU_CONTRASEÑA_LOCAL'
$env:FRONTEND_ORIGIN='http://localhost:5173'
# Opcional, solo en esta terminal; no guardes una clave real en Git.
$env:GEMINI_API_KEY='TU_CLAVE'
.\mvnw.cmd -B -ntp -pl backend package -DskipTests
java -jar backend/target/backend-1.0.0.jar
```

3. En otra terminal, desde la misma raíz:

```powershell
npm ci
npm run build
npm run preview
```

4. Abre [http://localhost:5173](http://localhost:5173). Las migraciones crean las tablas automáticamente; no ejecutes el SQL manualmente sobre una base que Flyway ya administra.

Si FFmpeg no está en el PATH, antes de iniciar Java define las rutas:

```powershell
$env:FFMPEG_BIN='C:\ruta\ffmpeg\bin\ffmpeg.exe'
$env:FFPROBE_BIN='C:\ruta\ffmpeg\bin\ffprobe.exe'
```

## Uso

Para desarrollar con recarga automática, usa `npm run dev` en lugar de `npm run preview`; ambos usan el puerto 5173 y se ejecutan uno a la vez. El backend sigue en el puerto 8080. Para otra dirección, configura `PUBLIC_API_URL` antes de iniciar Vite o compilar. `npm run typecheck` comprueba los tipos sin generar archivos.

1. Crea una cuenta y un canal desde **Mi estudio**.
2. En **Configuración**, agrega una palabra restringida, por ejemplo `palabra-prueba`, y guarda.
3. Escribe un título, selecciona categoría y presiona **Empezar transmisión**. Autoriza cámara y micrófono. La pantalla compartida depende del navegador; en móvil usa cámara.
4. En una ventana privada, abre la plataforma, entra al directo y crea otra cuenta para conversar. Usa audífonos para evitar realimentación de audio entre ambas ventanas.
5. Envía mensajes respetuosos y uno con la palabra restringida. Revisa la cola en **Moderación**. Invita a una tercera cuenta como moderador para mostrar permisos.
6. Tras al menos unos segundos, marca un momento. Abre **Biblioteca de clips**, reproduce el archivo, edita el recorte y publícalo. Los clips pendientes solo los puede reproducir su creador.
7. Con el modelo local y ngrok activos, usa **Asistente IA** para generar el análisis de la última transmisión.
8. Finaliza la transmisión antes de cerrar la pestaña del emisor. Si la conexión del emisor se pierde, el directo termina.

## Verificación

El backend expone [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). Debe devolver `{"status":"UP"}`.

Para ejecutar las pruebas crea una **base separada** `streamguard_test`, configura su conexión y ejecuta:

```powershell
$env:TEST_DATABASE_URL='jdbc:postgresql://localhost:5432/streamguard_test'
$env:TEST_PGUSER='streamguard'
$env:TEST_PGPASSWORD='TU_CONTRASEÑA_LOCAL'
.\mvnw.cmd -B -ntp -pl backend test
```

Las pruebas ejecutan las migraciones reales, registros, permisos, decisiones de moderación, sanciones, captura sintética de video y recorte con FFmpeg. **No apuntes las pruebas a la base de producción.** Sin variables de prueba, el puerto predeterminado es `55432`.

Con el backend y el frontend iniciados, y Google Chrome instalado, ejecuta las pruebas de navegador desde una tercera terminal:

```powershell
npm ci
npm run test:e2e
```

Se utiliza una cámara sintética. El informe se guarda en `artifacts/browser-test-results.json`; no se necesitan permisos para usar tu cámara real.

## Celular y PWA

La interfaz se adapta a pantallas pequeñas. En producción usa la URL HTTPS de Vercel y la opción del navegador para instalar/agregar a inicio. El diseño y el chat funcionan en móvil; las APIs de grabación y transcripción dependen del navegador. La API de cámara requiere HTTPS o localhost. Para probar desde un teléfono en desarrollo, utiliza una URL HTTPS de desarrollo y agrega ese origen al backend; una IP HTTP de la red local no equivale a localhost para los permisos de cámara.
