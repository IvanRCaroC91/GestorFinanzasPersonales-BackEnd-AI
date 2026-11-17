package com.smartkash.identidad.cuenta.dominio;

import java.util.Objects;

/**
 * Error que indica que el nombre de una cuenta no cumple sus reglas.
 *
 * <p>Qué hace: además del mensaje para personas, lleva el {@link Motivo}, que dice cuál de
 * las reglas del nombre se incumplió. Con él, quien reciba el error puede decidir qué
 * responder sin tener que leer el texto del mensaje.</p>
 *
 * <p>De dónde viene: lo lanza {@link Usuario} al crearse con un nombre vacío o demasiado
 * largo. A dónde va: lo recibe la funcionalidad que estaba creando la cuenta, que lo traduce
 * a su propio error. Por ejemplo, el registro lo convierte en su error de datos inválidos,
 * señalando el campo de la solicitud.</p>
 *
 * <p>Por qué es un error propio de las cuentas: las reglas del nombre pertenecen a la
 * cuenta, no a una funcionalidad concreta. Así este paquete no necesita conocer al registro
 * ni a ninguna otra funcionalidad que lo use.</p>
 *
 * <p>Reglas: el mensaje nunca incluye el nombre rechazado, porque los mensajes de error
 * suelen terminar en los logs.</p>
 *
 * <p>Es una excepción no comprobada (hereda de {@link RuntimeException}): Java no obliga a
 * declararla ni a capturarla en cada método por el que pasa.</p>
 */
public class NombreNoValido extends RuntimeException {

    /** Lista cerrada de las reglas que puede incumplir un nombre. */
    public enum Motivo {

        /** No hay nombre, o está vacío o hecho solo de espacios. */
        OBLIGATORIO,

        /** El nombre tiene más caracteres que el máximo. */
        MUY_LARGO
    }

    private final Motivo motivo;

    /**
     * Crea el error con la regla incumplida y su explicación.
     *
     * @param motivo  la regla que se incumplió; obligatorio
     * @param mensaje explicación para personas; no debe contener el nombre rechazado
     * @throws NullPointerException si el motivo es nulo
     */
    public NombreNoValido(Motivo motivo, String mensaje) {
        super(mensaje);
        this.motivo = Objects.requireNonNull(motivo, "El motivo es obligatorio.");
    }

    /**
     * Devuelve la regla que se incumplió.
     *
     * @return el motivo del error; nunca nulo
     */
    public Motivo motivo() {
        return motivo;
    }
}