-- =====================================================================
-- Servicio de finanzas · base de datos 'finanzas' · esquema inicial
-- Crea la tabla de cuentas del usuario. Las demás tablas llegarán en migraciones
-- posteriores, cuando se necesiten.
-- Una migración ya aplicada no se modifica: los cambios van en un archivo nuevo.
-- =====================================================================

-- Se ejecuta con el rol finanzas_migrador (dueño del esquema). La aplicación usa finanzas_app.

CREATE TABLE cuentas (
                         id               uuid PRIMARY KEY,
                         usuario_ref      uuid NOT NULL,
                         tipo             varchar(10) NOT NULL CHECK (tipo IN ('EFECTIVO', 'AHORROS', 'CORRIENTE', 'BILLETERA')),
                         nombre           varchar(60) NOT NULL,
                         saldo_inicial    numeric(19,2) NOT NULL DEFAULT 0,
                         fecha_inicio     date NOT NULL,
                         predeterminada   boolean NOT NULL DEFAULT false,
                         archivada        boolean NOT NULL DEFAULT false,
                         creado_en        timestamptz NOT NULL DEFAULT now(),
                         actualizado_en   timestamptz NOT NULL DEFAULT now(),
                         version          bigint NOT NULL DEFAULT 0
);

-- Un usuario no puede tener dos cuentas con el mismo nombre, sin importar mayúsculas.
CREATE UNIQUE INDEX ix_cuentas_nombre ON cuentas (usuario_ref, lower(nombre));
-- Y solo una cuenta puede ser la predeterminada.
CREATE UNIQUE INDEX ix_cuentas_predeterminada ON cuentas (usuario_ref) WHERE predeterminada;

COMMENT ON TABLE cuentas IS 'Cuentas financieras del usuario.';
COMMENT ON COLUMN cuentas.usuario_ref IS 'Dueño de la cuenta: el identificador que emite el servicio de identidad. No es clave foránea porque vive en otra base de datos.';
COMMENT ON COLUMN cuentas.saldo_inicial IS 'Saldo en la fecha de inicio; puede ser negativo en una cuenta corriente.';
COMMENT ON COLUMN cuentas.fecha_inicio IS 'Fecha desde la que se calcula el saldo.';
COMMENT ON COLUMN cuentas.predeterminada IS 'Cuenta sugerida por defecto.';
COMMENT ON COLUMN cuentas.archivada IS 'Oculta para registros nuevos.';
COMMENT ON COLUMN cuentas.version IS 'Bloqueo optimista: evita que dos cambios simultáneos se pisen.';

-- Permisos: el rol de la aplicación solo trabaja con los datos de esta tabla. Cada migración
-- futura debe conceder los suyos sobre sus tablas nuevas.
GRANT SELECT, INSERT, UPDATE, DELETE ON cuentas TO finanzas_app;