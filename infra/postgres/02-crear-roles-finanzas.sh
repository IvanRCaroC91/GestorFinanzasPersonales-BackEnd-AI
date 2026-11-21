#!/bin/bash
# SOLO PARA DESARROLLO LOCAL.
#
# Prepara la base de datos del servicio de finanzas. PostgreSQL ejecuta este script una
# sola vez, la primera vez que arranca con la carpeta de datos vacia.
#
# Crea dos usuarios con permisos distintos:
#   - El migrador: es el dueno de la base. Lo usa Flyway para crear y cambiar tablas.
#   - El de la aplicacion: lo usa el servicio. Solo puede leer y escribir datos, con los
#     permisos que le conceden las migraciones. No puede crear ni cambiar tablas.
#
# Los nombres y las contrasenas llegan por variables de entorno (ver compose.yaml y .env).

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v migrador="$FINANZAS_BD_MIGRADOR_USUARIO" \
    -v migrador_contrasena="$FINANZAS_BD_MIGRADOR_CONTRASENA" \
    -v aplicacion="$FINANZAS_BD_USUARIO" \
    -v aplicacion_contrasena="$FINANZAS_BD_CONTRASENA" <<'EOSQL'
CREATE ROLE :"migrador" LOGIN PASSWORD :'migrador_contrasena';
CREATE ROLE :"aplicacion" LOGIN PASSWORD :'aplicacion_contrasena';

CREATE DATABASE finanzas OWNER :"migrador";

-- Solo estos dos usuarios pueden conectarse a la base de finanzas.
REVOKE CONNECT ON DATABASE finanzas FROM PUBLIC;
GRANT CONNECT ON DATABASE finanzas TO :"aplicacion";
EOSQL