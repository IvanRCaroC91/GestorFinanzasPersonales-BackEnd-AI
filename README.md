# SmartKash Backend

Backend del Gestor de Finanzas Personales (TFM UNIR). Maven multimódulo con Java 25 y Spring Boot 4.1.

## Módulos
- `nucleo-financiero`: tipos compartidos sin framework (Dinero, TasaInteres, Periodo).
- `nucleo-web`: errores RFC 9457 e idempotencia.
- `eureka`: servidor de descubrimiento privado, donde los servicios se registran por su nombre.
- `svc-identidad`: registro, verificación del correo, inicio de sesión y tokens de acceso.

## Compilar
./mvnw verify

Las pruebas de integración arrancan PostgreSQL y Mailpit en Docker con Testcontainers, así que
necesitan Docker Desktop abierto. No hace falta ningún archivo `.env` ni ninguna clave: las
pruebas generan las suyas.

## Arranque local del servicio de identidad

> El archivo `compose.yaml` y la carpeta `infra/` son **solo para desarrollo local**. No sirven
> para desplegar: la base de datos queda en un contenedor de tu equipo, sin copias de seguridad
> ni cifrado de conexiones, y los correos no salen de tu equipo.

Necesitas Docker Desktop abierto y `JAVA_HOME` apuntando a un JDK 25.

1. **Crea la clave de firma de los tokens**, fuera de la carpeta del repositorio. Es una clave
   privada RSA de 3072 bits en formato PEM (PKCS#8). En Windows, `openssl` viene con Git: ábrelo
   desde Git Bash.

       openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out <ruta-fuera-del-repo>/firma.pem

   Es un secreto: no la compartas ni la copies dentro del repositorio (los archivos `.pem` y
   `.key` están en `.gitignore` por si acaso). Si se pierde, basta con crear otra; las sesiones
   abiertas dejarán de valer.

2. **Prepara las variables.** Copia `.env.ejemplo` como `.env` y rellena cada valor; el archivo
   explica cada uno. El archivo `.env` no se sube al repositorio. Las del servicio de identidad:

   | Variable | Para qué |
   |---|---|
   | `IDENTIDAD_BD_*` | Conexión a la base de datos y usuarios. |
   | `IDENTIDAD_CLAVE_FIRMA_RUTA` | Ruta del archivo `firma.pem` del paso 1. |
   | `IDENTIDAD_TOKEN_EMISOR` | Quién emite los tokens (`iss`), por ejemplo `http://localhost:8080`. |
   | `IDENTIDAD_SMTP_HOST`, `IDENTIDAD_SMTP_PUERTO` | Servidor de correo: el Mailpit local (`127.0.0.1`, `1025`). |
   | `IDENTIDAD_CORREO_REMITENTE` | Dirección que aparece como remitente. |
   | `IDENTIDAD_ENLACE_BASE` | Dirección de la web a la que apunta el enlace de verificación. |
   | `EUREKA_USUARIO`, `EUREKA_CONTRASENA` | Credenciales del servidor de descubrimiento. Las usan Eureka y los servicios que se registran en él. |

   Si falta alguna, el servicio no arranca y el error dice cuál. Las credenciales de Eureka
   viven solo en tu `.env`: nunca se escriben en el repositorio.

3. **Levanta PostgreSQL 18 y Mailpit:** `docker compose up -d`
   La primera vez se crean la base `identidad` y sus dos usuarios: el que crea las tablas y
   el que usa el servicio, que solo puede leer y escribir datos.

4. **Arranca Eureka** (opcional: identidad arranca igualmente sin él y reintenta el registro en
   segundo plano), en una ventana de PowerShell aparte, con las variables `EUREKA_USUARIO` y
   `EUREKA_CONTRASENA` de tu `.env` cargadas en esa ventana:

       ./mvnw -pl eureka spring-boot:run

   Escucha en el puerto `8761` y solo en `127.0.0.1`: no es alcanzable desde otros equipos de
   la red. Todas sus rutas piden usuario y contraseña, y su panel web está desactivado.

5. **Arranca el servicio:** `./infra/local/arrancar-identidad.ps1`
   Queda escuchando en `http://localhost:8080`. Se detiene con `Ctrl + C`. El script carga
   también las variables `EUREKA_*`, con las que el servicio se registra en Eureka.

   Para comprobar que identidad aparece registrada (puede tardar unos segundos tras arrancar,
   a veces hasta un minuto), pide su ficha a Eureka. Escribe solo el usuario: `curl` pedirá la contraseña sin mostrarla
   ni dejarla en el historial.

       curl.exe -u <usuario-de-eureka> -H "Accept: application/json" http://127.0.0.1:8761/eureka/apps/SVC-IDENTIDAD

   Si está registrada, la respuesta incluye `"status": "UP"`. Un 404 significa que aún no se ha
   registrado; un 401, que las credenciales no coinciden.

6. **Mira los correos** en `http://localhost:8025`. Mailpit es un buzón de pruebas: acepta todos
   los correos que envía el servicio y no entrega ninguno. Ahí aparece el correo de verificación
   con su enlace. Al parar el contenedor, los correos se pierden.

7. **Prueba con Postman** importando las colecciones de `infra/postman/`:
   - `registro-usuario.postman_collection.json`: el registro y sus errores.
   - `inicio-de-sesion.postman_collection.json`: el recorrido completo. Registra una cuenta, lee
     el enlace en Mailpit, inicia sesión, pide otro enlace, verifica, consulta las claves públicas
     y comprueba el bloqueo. Se puede ejecutar entera con el Runner de Postman. Su descripción
     explica dos avisos: bloquea la cuenta de prueba un minuto al final, y cada ejecución cuenta
     7 fallos desde tu IP (con 20 en 15 minutos la IP queda bloqueada).

   La cookie de renovación lleva el atributo `Secure`, que normalmente exige HTTPS. Los navegadores
   y Postman la aceptan igualmente en `http://localhost`.

Para parar los contenedores: `docker compose down`. Para borrar la base de datos y empezar de
cero (por ejemplo, si cambias las contraseñas de `.env`): `docker compose down -v`.

## Rutas del servicio de identidad

| Ruta | Acceso | Qué hace |
|---|---|---|
| `POST /api/v1/auth/registro` | Pública | Crea la cuenta y envía el enlace de verificación. |
| `POST /api/v1/auth/inicio-sesion` | Pública | Devuelve el token de acceso (15 min) y pone la cookie de renovación (7 días). |
| `POST /api/v1/auth/verificacion-correo` | Pública | Verifica el correo con el token del enlace. |
| `POST /api/v1/auth/verificacion-correo/reenvio` | Token de acceso | Envía otro enlace (como mucho 3 por hora). |
| `GET /.well-known/jwks.json` | Pública | Claves públicas para comprobar los tokens. |

Cualquier otra ruta exige `Authorization: Bearer <token de acceso>`. Los errores siguen el formato
RFC 9457 (un estándar público para describir errores de una API web).