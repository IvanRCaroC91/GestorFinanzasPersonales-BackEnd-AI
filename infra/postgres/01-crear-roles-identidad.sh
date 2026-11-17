#!/bin/bash
# SOLO PARA DESARROLLO LOCAL.
#
# Prepara la base de datos del servicio de identidad. PostgreSQL ejecuta este script una
# sola vez, la primera vez que arranca con la carpeta de datos vacia.
#
# Crea dos usuarios con permisos distintos:
#   - El migrador: es el dueno de la base. Lo usa Flyway para crear y cambiar tablas.
#   - El de la aplicacion: lo usa el servicio. Solo puede leer y escribir datos, con los
#     permisos que le conceden las migraciones. No puede crear ni cambiar tablas.
#
# Los nombres y las contrasenas llegan por variables de entorno (ver compose.yaml y .env);
# aqui no se escribe ninguna. Se pasan a psql como variables para que cualquier caracter
# especial de una contrasena quede bien escapado.

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v migrador="$IDENTIDAD_BD_MIGRADOR_USUARIO" \
    -v migrador_contrasena="$IDENTIDAD_BD_MIGRADOR_CONTRASENA" \
    -v aplicacion="$IDENTIDAD_BD_USUARIO" \
    -v aplicacion_contrasena="$IDENTIDAD_BD_CONTRASENA" <<'EOSQL'
CREATE ROLE :"migrador" LOGIN PASSWORD :'migrador_contrasena';
CREATE ROLE :"aplicacion" LOGIN PASSWORD :'aplicacion_contrasena';

CREATE DATABASE identidad OWNER :"migrador";

-- Solo estos dos usuarios pueden conectarse a la base de identidad.
REVOKE CONNECT ON DATABASE identidad FROM PUBLIC;
GRANT CONNECT ON DATABASE identidad TO :"aplicacion";
EOSQL
