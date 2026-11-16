package com.smartkash.identidad.registro.dominio;

/**
 * Rol de autorización de una cuenta: qué tipo de acciones puede hacer en el sistema.
 *
 * <p>Los nombres coinciden exactamente con los valores que admite la columna de rol en
 * la tabla de usuarios. El rol lo decide siempre el servidor; nunca se toma del cuerpo
 * de una solicitud.</p>
 */
public enum Rol {

    /** Persona que gestiona sus propias finanzas. Es el rol de toda cuenta recién creada. */
    USUARIO,

    /** Persona que administra la plataforma. */
    ADMIN
}
