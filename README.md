# SmartKash Backend

Backend del Gestor de Finanzas Personales (TFM UNIR). Maven multimódulo con Java 25 y Spring Boot 4.1.

## Módulos
- `nucleo-financiero`: tipos compartidos sin framework (Dinero, TasaInteres, Periodo).
- `nucleo-web`: errores RFC 9457 e idempotencia.

## Compilar
./mvnw verify

## Arranque local del servicio de identidad

> El archivo `compose.yaml` y la carpeta `infra/` son **solo para desarrollo local**. No sirven
> para desplegar: la base de datos queda en un contenedor de tu equipo, sin copias de seguridad
> ni cifrado de conexiones.

Necesitas Docker Desktop abierto y `JAVA_HOME` apuntando a un JDK 25.

1. Copia `.env.ejemplo` como `.env` y cambia cada `cambiar-esto` por una contraseña tuya.
   El archivo `.env` no se sube al repositorio.
2. Levanta PostgreSQL 18: `docker compose up -d`
   La primera vez se crean la base `identidad` y sus dos usuarios: el que crea las tablas y
   el que usa el servicio, que solo puede leer y escribir datos.
3. Arranca el servicio: `./infra/local/arrancar-identidad.ps1`
   Queda escuchando en `http://localhost:8080`. Se detiene con `Ctrl + C`.
4. Prueba el registro importando en Postman la colección
   `infra/postman/registro-usuario.postman_collection.json`.

Para parar la base de datos: `docker compose down`. Para borrarla y empezar de cero (por
ejemplo, si cambias las contraseñas de `.env`): `docker compose down -v`.