-- =====================================================================
-- Servicio de identidad · base de datos 'identidad' · esquema inicial
-- Crea todas las tablas del servicio: usuarios, tokens, autorizaciones, auditoría
-- y las tablas de apoyo para entregar eventos y evitar operaciones repetidas.
-- Una migración ya aplicada no se modifica: los cambios van en un archivo nuevo.
-- =====================================================================

-- Se ejecuta con el rol identidad_migrador (dueño del esquema). La aplicación usa identidad_app.

CREATE TABLE outbox (
    id                           uuid PRIMARY KEY,
    tipo_evento                  varchar(60) NOT NULL,
    destino                      varchar(60) NOT NULL,
    usuario_ref                  uuid,
    carga                        jsonb NOT NULL,
    estado                       varchar(12) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'ENTREGADO', 'FALLIDO')),
    intentos                     smallint NOT NULL DEFAULT 0,
    proximo_intento_en           timestamptz NOT NULL DEFAULT now(),
    ultimo_error                 varchar(500),
    creado_en                    timestamptz NOT NULL DEFAULT now(),
    entregado_en                 timestamptz
);
CREATE INDEX ix_outbox_1 ON outbox (estado, proximo_intento_en) WHERE estado = 'PENDIENTE';
COMMENT ON TABLE outbox IS 'Eventos pendientes de entregar a otros servicios en la misma transacción que el cambio de negocio, para que nunca se pierda un evento ni se envíe uno de un cambio que no se guardó.';
COMMENT ON COLUMN outbox.id IS 'Identificador del evento; se usa como Idempotency-Key';
COMMENT ON COLUMN outbox.tipo_evento IS 'PagoDeudaRegistrado, CuotaPorVencer, FacturaConfirmada, UsuarioEliminado';
COMMENT ON COLUMN outbox.destino IS 'Servicio y ruta interna de destino';
COMMENT ON COLUMN outbox.usuario_ref IS 'Usuario afectado (si aplica)';
COMMENT ON COLUMN outbox.carga IS 'Datos del evento (sin datos personales innecesarios)';
COMMENT ON COLUMN outbox.estado IS 'Estado de entrega';
COMMENT ON COLUMN outbox.intentos IS 'Número de intentos realizados';
COMMENT ON COLUMN outbox.proximo_intento_en IS 'Cuándo reintentar';
COMMENT ON COLUMN outbox.ultimo_error IS 'Último error resumido (sin datos personales)';
COMMENT ON COLUMN outbox.creado_en IS 'Momento de creación';
COMMENT ON COLUMN outbox.entregado_en IS 'Momento de entrega confirmada';

CREATE TABLE idempotencia (
    ambito                       varchar(80) NOT NULL,
    clave                        varchar(100) NOT NULL,
    usuario_ref                  uuid,
    hash_solicitud               char(64) NOT NULL,
    codigo_respuesta             smallint NOT NULL,
    cuerpo_respuesta             jsonb,
    creado_en                    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_idempotencia PRIMARY KEY (ambito, clave)
);
COMMENT ON TABLE idempotencia IS 'Claves de idempotencia ya procesadas por este servicio y su respuesta, para devolver lo mismo ante reintentos.';
COMMENT ON COLUMN idempotencia.ambito IS 'Operación protegida';
COMMENT ON COLUMN idempotencia.clave IS 'Valor de Idempotency-Key';
COMMENT ON COLUMN idempotencia.usuario_ref IS 'Usuario de la solicitud';
COMMENT ON COLUMN idempotencia.hash_solicitud IS 'SHA-256 del cuerpo; si llega la misma clave con otro cuerpo se responde 422';
COMMENT ON COLUMN idempotencia.codigo_respuesta IS 'Código HTTP devuelto';
COMMENT ON COLUMN idempotencia.cuerpo_respuesta IS 'Respuesta devuelta';
COMMENT ON COLUMN idempotencia.creado_en IS 'Momento de procesamiento';

CREATE TABLE auditoria (
    id                           uuid PRIMARY KEY,
    ocurrido_en                  timestamptz NOT NULL DEFAULT now(),
    actor_tipo                   varchar(10) NOT NULL CHECK (actor_tipo IN ('USUARIO', 'SISTEMA', 'ADMIN')),
    actor_ref                    uuid,
    propietario_ref              uuid NOT NULL,
    entidad                      varchar(40) NOT NULL,
    entidad_id                   uuid NOT NULL,
    accion                       varchar(12) NOT NULL CHECK (accion IN ('CREAR', 'MODIFICAR', 'ANULAR', 'ELIMINAR')),
    antes                        jsonb,
    despues                      jsonb,
    trace_id                     varchar(32)
);
CREATE INDEX ix_auditoria_1 ON auditoria (propietario_ref, entidad, entidad_id, ocurrido_en);
COMMENT ON TABLE auditoria IS 'Registro de solo inserción de cambios sobre datos financieros. El rol de la aplicación no tiene UPDATE ni DELETE; la purga por supresión usa una función SECURITY DEFINER.';
COMMENT ON COLUMN auditoria.id IS 'Identificador del evento';
COMMENT ON COLUMN auditoria.ocurrido_en IS 'Momento del cambio';
COMMENT ON COLUMN auditoria.actor_tipo IS 'Quién hizo el cambio';
COMMENT ON COLUMN auditoria.actor_ref IS 'Usuario o servicio que actuó';
COMMENT ON COLUMN auditoria.propietario_ref IS 'Usuario dueño del registro';
COMMENT ON COLUMN auditoria.entidad IS 'Tabla o agregado afectado';
COMMENT ON COLUMN auditoria.entidad_id IS 'Identificador del registro';
COMMENT ON COLUMN auditoria.accion IS 'Tipo de cambio';
COMMENT ON COLUMN auditoria.antes IS 'Valores anteriores (solo campos de negocio)';
COMMENT ON COLUMN auditoria.despues IS 'Valores nuevos';
COMMENT ON COLUMN auditoria.trace_id IS 'Traza W3C de la solicitud';

CREATE TABLE usuarios (
    id                           uuid PRIMARY KEY,
    email                        varchar(254) NOT NULL,
    email_normalizado            varchar(254) NOT NULL UNIQUE,
    nombre                       varchar(100) NOT NULL,
    hash_contrasena              varchar(255) NOT NULL,
    rol                          varchar(10) NOT NULL DEFAULT 'USUARIO' CHECK (rol IN ('USUARIO', 'ADMIN')),
    estado                       varchar(24) NOT NULL DEFAULT 'PENDIENTE_VERIFICACION' CHECK (estado IN ('PENDIENTE_VERIFICACION', 'ACTIVA', 'PENDIENTE_ELIMINACION')),
    verificado_en                timestamptz,
    eliminacion_programada_en    timestamptz,
    creado_en                    timestamptz NOT NULL DEFAULT now(),
    actualizado_en               timestamptz NOT NULL DEFAULT now(),
    version                      bigint NOT NULL DEFAULT 0
);
COMMENT ON TABLE usuarios IS 'Cuentas de usuario y su estado.';
COMMENT ON COLUMN usuarios.id IS 'Identificador del usuario (sub del JWT)';
COMMENT ON COLUMN usuarios.email IS 'Correo tal como lo escribió el usuario';
COMMENT ON COLUMN usuarios.email_normalizado IS 'Correo en minúsculas y sin espacios; impide dos cuentas con el mismo correo';
COMMENT ON COLUMN usuarios.nombre IS 'Nombre para mostrar';
COMMENT ON COLUMN usuarios.hash_contrasena IS 'Hash Argon2id con parámetros codificados; nunca se guarda la contraseña';
COMMENT ON COLUMN usuarios.rol IS 'Rol de autorización';
COMMENT ON COLUMN usuarios.estado IS 'Estado de la cuenta';
COMMENT ON COLUMN usuarios.verificado_en IS 'Verificación del correo';
COMMENT ON COLUMN usuarios.eliminacion_programada_en IS 'Fecha de borrado definitivo si hay una solicitud de supresión';
COMMENT ON COLUMN usuarios.creado_en IS 'Alta';
COMMENT ON COLUMN usuarios.actualizado_en IS 'Última modificación';
COMMENT ON COLUMN usuarios.version IS 'Bloqueo optimista';

CREATE TABLE bloqueos_acceso (
    clave_hash                   char(64) PRIMARY KEY,
    tipo                         varchar(6) NOT NULL CHECK (tipo IN ('CUENTA', 'IP')),
    fallos                       smallint NOT NULL DEFAULT 0,
    ventana_inicio               timestamptz NOT NULL,
    bloqueado_hasta              timestamptz
);
COMMENT ON TABLE bloqueos_acceso IS 'Contadores de intentos fallidos por cuenta y por IP, para frenar los intentos repetidos de adivinar contraseñas.';
COMMENT ON COLUMN bloqueos_acceso.clave_hash IS 'SHA-256 de ''CUENTA:<correo normalizado>'' o ''IP:<ip>'' (no se guardan en claro)';
COMMENT ON COLUMN bloqueos_acceso.tipo IS 'Tipo de contador';
COMMENT ON COLUMN bloqueos_acceso.fallos IS 'Fallos en la ventana';
COMMENT ON COLUMN bloqueos_acceso.ventana_inicio IS 'Inicio de la ventana de 15 min';
COMMENT ON COLUMN bloqueos_acceso.bloqueado_hasta IS 'Fin del bloqueo (espera creciente)';

CREATE TABLE tokens_renovacion (
    id                           uuid PRIMARY KEY,
    familia_id                   uuid NOT NULL,
    usuario_id                   uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    hash_token                   char(64) NOT NULL UNIQUE,
    emitido_en                   timestamptz NOT NULL DEFAULT now(),
    expira_en                    timestamptz NOT NULL,
    usado_en                     timestamptz,
    revocado_en                  timestamptz,
    motivo_revocacion            varchar(30)
);
CREATE INDEX ix_tokens_renovacion_1 ON tokens_renovacion (usuario_id);
CREATE INDEX ix_tokens_renovacion_2 ON tokens_renovacion (familia_id);
COMMENT ON TABLE tokens_renovacion IS 'Tokens de renovación rotativos agrupados por familia (sesión). Solo se guarda su hash.';
COMMENT ON COLUMN tokens_renovacion.id IS 'Identificador';
COMMENT ON COLUMN tokens_renovacion.familia_id IS 'Sesión a la que pertenece; se revoca entera ante una reutilización';
COMMENT ON COLUMN tokens_renovacion.usuario_id IS 'Dueño';
COMMENT ON COLUMN tokens_renovacion.hash_token IS 'SHA-256 del valor entregado en la cookie';
COMMENT ON COLUMN tokens_renovacion.emitido_en IS 'Emisión';
COMMENT ON COLUMN tokens_renovacion.expira_en IS 'Caducidad (≤ 7 días)';
COMMENT ON COLUMN tokens_renovacion.usado_en IS 'Momento en que se rotó';
COMMENT ON COLUMN tokens_renovacion.revocado_en IS 'Revocación';
COMMENT ON COLUMN tokens_renovacion.motivo_revocacion IS 'CIERRE_SESION, REUTILIZACION, CAMBIO_CONTRASENA, SUPRESION';

CREATE TABLE tokens_un_solo_uso (
    id                           uuid PRIMARY KEY,
    usuario_id                   uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo                         varchar(20) NOT NULL CHECK (tipo IN ('VERIFICACION_CORREO', 'RECUPERACION')),
    hash_token                   char(64) NOT NULL UNIQUE,
    expira_en                    timestamptz NOT NULL,
    usado_en                     timestamptz,
    creado_en                    timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE tokens_un_solo_uso IS 'Enlaces de verificación de correo y de recuperación de contraseña.';
COMMENT ON COLUMN tokens_un_solo_uso.id IS 'Identificador';
COMMENT ON COLUMN tokens_un_solo_uso.usuario_id IS 'Dueño';
COMMENT ON COLUMN tokens_un_solo_uso.tipo IS 'Finalidad';
COMMENT ON COLUMN tokens_un_solo_uso.hash_token IS 'SHA-256 del valor enviado por correo';
COMMENT ON COLUMN tokens_un_solo_uso.expira_en IS '24 h (verificación) o 30 min (recuperación)';
COMMENT ON COLUMN tokens_un_solo_uso.usado_en IS 'Uso';
COMMENT ON COLUMN tokens_un_solo_uso.creado_en IS 'Creación';

CREATE TABLE politicas_tratamiento (
    version                      varchar(10) PRIMARY KEY,
    publicada_en                 timestamptz NOT NULL,
    url                          varchar(300) NOT NULL,
    hash_texto                   char(64) NOT NULL,
    cambia_finalidad             boolean NOT NULL DEFAULT false
);
COMMENT ON TABLE politicas_tratamiento IS 'Versiones publicadas de la política de tratamiento de datos. El registro exige aceptar la vigente.';
COMMENT ON COLUMN politicas_tratamiento.version IS 'Versión (por ejemplo, 1.0)';
COMMENT ON COLUMN politicas_tratamiento.publicada_en IS 'Publicación';
COMMENT ON COLUMN politicas_tratamiento.url IS 'Ubicación pública del texto';
COMMENT ON COLUMN politicas_tratamiento.hash_texto IS 'SHA-256 del texto publicado (prueba de qué se aceptó)';
COMMENT ON COLUMN politicas_tratamiento.cambia_finalidad IS 'Si exige una nueva autorización a los usuarios existentes';

CREATE TABLE autorizaciones (
    id                           uuid PRIMARY KEY,
    usuario_id                   uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo                         varchar(24) NOT NULL CHECK (tipo IN ('POLITICA_TRATAMIENTO', 'USO_IA')),
    politica_version             varchar(10) REFERENCES politicas_tratamiento (version) ON DELETE RESTRICT,
    otorgada                     boolean NOT NULL,
    canal                        varchar(10) NOT NULL DEFAULT 'WEB',
    registrada_en                timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_autorizaciones_1 ON autorizaciones (usuario_id, tipo, registrada_en DESC);
COMMENT ON TABLE autorizaciones IS 'Registro de solo inserción de autorizaciones otorgadas y revocadas (prueba exigida por el Decreto 1377). La vigente es la última por usuario y tipo.';
COMMENT ON COLUMN autorizaciones.id IS 'Identificador';
COMMENT ON COLUMN autorizaciones.usuario_id IS 'Titular';
COMMENT ON COLUMN autorizaciones.tipo IS 'Autorización';
COMMENT ON COLUMN autorizaciones.politica_version IS 'Versión aceptada (para POLITICA_TRATAMIENTO)';
COMMENT ON COLUMN autorizaciones.otorgada IS 'true = otorga; false = revoca';
COMMENT ON COLUMN autorizaciones.canal IS 'Canal';
COMMENT ON COLUMN autorizaciones.registrada_en IS 'Momento';

CREATE TABLE clientes_servicio (
    client_id                    varchar(40) PRIMARY KEY,
    hash_secreto                 varchar(255) NOT NULL,
    scopes                       text[] NOT NULL,
    activo                       boolean NOT NULL DEFAULT true
);
COMMENT ON TABLE clientes_servicio IS 'Credenciales de los servicios internos para obtener tokens de servicio.';
COMMENT ON COLUMN clientes_servicio.client_id IS 'Identificador (svc-finanzas, svc-deudas…)';
COMMENT ON COLUMN clientes_servicio.hash_secreto IS 'Hash Argon2 del secreto';
COMMENT ON COLUMN clientes_servicio.scopes IS 'Permisos internos que puede pedir';
COMMENT ON COLUMN clientes_servicio.activo IS 'Habilitado';

CREATE TABLE claves_firma (
    kid                          varchar(40) PRIMARY KEY,
    clave_publica_jwk            jsonb NOT NULL,
    estado                       varchar(10) NOT NULL CHECK (estado IN ('ACTIVA', 'RETIRADA')),
    creada_en                    timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE claves_firma IS 'Claves públicas publicadas en el JWKS. La clave privada vive en secretos, nunca en la BD.';
COMMENT ON COLUMN claves_firma.kid IS 'Identificador de la clave';
COMMENT ON COLUMN claves_firma.clave_publica_jwk IS 'Clave pública en formato JWK';
COMMENT ON COLUMN claves_firma.estado IS 'Estado';
COMMENT ON COLUMN claves_firma.creada_en IS 'Creación';

CREATE TABLE solicitudes_supresion (
    id                           uuid PRIMARY KEY,
    usuario_ref                  uuid NOT NULL,
    solicitada_en                timestamptz NOT NULL DEFAULT now(),
    ejecutar_en                  timestamptz NOT NULL,
    estado                       varchar(14) NOT NULL CHECK (estado IN ('PROGRAMADA', 'CANCELADA', 'EN_CURSO', 'COMPLETADA', 'CON_ERRORES')),
    completada_en                timestamptz
);
COMMENT ON TABLE solicitudes_supresion IS 'Solicitudes de eliminación de cuenta y su avance. Sobrevive a la purga sin datos personales.';
COMMENT ON COLUMN solicitudes_supresion.id IS 'Identificador';
COMMENT ON COLUMN solicitudes_supresion.usuario_ref IS 'Usuario (sin FK para sobrevivir a la purga)';
COMMENT ON COLUMN solicitudes_supresion.solicitada_en IS 'Solicitud';
COMMENT ON COLUMN solicitudes_supresion.ejecutar_en IS 'Fin del periodo de gracia';
COMMENT ON COLUMN solicitudes_supresion.estado IS 'Avance';
COMMENT ON COLUMN solicitudes_supresion.completada_en IS 'Fin de la purga';

CREATE TABLE confirmaciones_supresion (
    solicitud_id                 uuid NOT NULL REFERENCES solicitudes_supresion (id) ON DELETE CASCADE,
    servicio                     varchar(30) NOT NULL,
    confirmada_en                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_confirmaciones_supresion PRIMARY KEY (solicitud_id, servicio)
);
COMMENT ON TABLE confirmaciones_supresion IS 'Confirmación de purga de cada servicio.';
COMMENT ON COLUMN confirmaciones_supresion.solicitud_id IS 'Solicitud';
COMMENT ON COLUMN confirmaciones_supresion.servicio IS 'Servicio que confirmó';
COMMENT ON COLUMN confirmaciones_supresion.confirmada_en IS 'Momento';

-- ---------------------------------------------------------------- permisos
-- La aplicación se conecta con un rol distinto del que crea las tablas y solo recibe
-- los permisos que necesita para trabajar con los datos.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO identidad_app;
REVOKE UPDATE, DELETE, TRUNCATE ON auditoria FROM identidad_app;  -- auditoría de solo inserción

-- Única vía para seudonimizar la auditoría de un usuario que eliminó su cuenta.
CREATE FUNCTION purgar_auditoria_usuario(p_propietario uuid) RETURNS integer
    LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    WITH a AS (
        UPDATE auditoria SET antes = NULL, despues = NULL, actor_ref = NULL
        WHERE propietario_ref = p_propietario
        RETURNING 1)
    SELECT count(*)::integer FROM a;
$$;
REVOKE ALL ON FUNCTION purgar_auditoria_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION purgar_auditoria_usuario(uuid) TO identidad_app;
