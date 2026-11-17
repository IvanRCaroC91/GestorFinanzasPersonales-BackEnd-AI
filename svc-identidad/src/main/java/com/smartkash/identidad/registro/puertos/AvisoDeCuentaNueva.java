package com.smartkash.identidad.registro.puertos;

import com.smartkash.identidad.cuenta.dominio.Usuario;

/**
 * Avisa de que el registro acaba de crear una cuenta nueva.
 *
 * <p>Qué es: un puerto. El registro no sabe qué pasa después de crear una cuenta (hoy, enviar
 * el primer enlace de verificación); solo avisa. Así el registro no depende de la verificación
 * del correo.</p>
 *
 * <p>Regla: se llama dentro de la transacción del registro. Quien lo implemente debe actuar
 * solo si esa transacción se confirma: si el registro se deshace, la cuenta no existe y no hay
 * nada que hacer.</p>
 */
@FunctionalInterface
public interface AvisoDeCuentaNueva {

    /**
     * Avisa de que la cuenta se creó.
     *
     * <p>Solo se llama para cuentas nuevas, nunca cuando el correo ya estaba registrado.</p>
     *
     * @param cuenta la cuenta recién guardada
     */
    void cuentaCreada(Usuario cuenta);
}