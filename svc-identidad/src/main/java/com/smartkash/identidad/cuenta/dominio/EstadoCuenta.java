package com.smartkash.identidad.cuenta.dominio;

/**
 * Estado en el que se encuentra una cuenta a lo largo de su vida.
 *
 * <p>Los nombres coinciden exactamente con los valores que admite la columna de estado
 * en la tabla de usuarios. El estado lo decide siempre el servidor; nunca se toma del
 * cuerpo de una solicitud.</p>
 */
public enum EstadoCuenta {

    /** La cuenta existe pero la persona aún no ha confirmado su correo. Así nace toda cuenta. */
    PENDIENTE_VERIFICACION,

    /** La persona confirmó su correo y puede usar la cuenta con normalidad. */
    ACTIVA,

    /** La persona pidió eliminar su cuenta y está corriendo el plazo antes del borrado. */
    PENDIENTE_ELIMINACION
}
