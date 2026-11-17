package com.smartkash.identidad.cuenta.dominio;

import java.util.Objects;

/**
 * Evento: se registró una cuenta nueva.
 *
 * <p>Qué es un evento: un aviso de que algo ocurrió. Quien lo publica no sabe quién lo
 * escucha. Aquí lo publica el registro y lo escucha la verificación del correo, que envía el
 * primer enlace. Vive en el paquete compartido de cuentas para que ninguna de las dos
 * funcionalidades dependa de la otra.</p>
 *
 * @param cuenta la cuenta recién creada; su texto de depuración no muestra datos personales
 */
public record CuentaRegistrada(Usuario cuenta) {

    /**
     * Comprueba que lleva la cuenta.
     *
     * @throws NullPointerException si falta la cuenta
     */
    public CuentaRegistrada {
        Objects.requireNonNull(cuenta, "La cuenta es obligatoria.");
    }
}