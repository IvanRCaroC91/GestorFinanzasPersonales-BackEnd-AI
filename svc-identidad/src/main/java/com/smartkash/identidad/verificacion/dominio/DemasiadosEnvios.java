package com.smartkash.identidad.verificacion.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * Error de una petición de reenvío cuando ya se alcanzó el tope de enlaces por hora.
 *
 * <p>Qué lleva: el tiempo que falta para poder pedir otro, calculado con
 * {@link TopeDeEnvios#esperaHastaOtro}.</p>
 *
 * <p>A dónde va: la capa web lo convierte en un {@code 429} con la cabecera
 * {@code Retry-After}.</p>
 */
public final class DemasiadosEnvios extends RuntimeException {

    /** Cuánto falta para poder pedir otro enlace. */
    private final Duration espera;

    /**
     * Crea el error con el tiempo de espera.
     *
     * @param espera cuánto falta para poder pedir otro enlace
     * @throws NullPointerException si falta la espera
     */
    public DemasiadosEnvios(Duration espera) {
        super("Ya pediste " + TopeDeEnvios.MAXIMO + " enlaces en la última hora. "
                + "Espera antes de pedir otro.");
        this.espera = Objects.requireNonNull(espera, "La espera es obligatoria.");
    }

    /**
     * Devuelve cuánto falta para poder pedir otro enlace.
     *
     * @return el tiempo de espera
     */
    public Duration espera() {
        return espera;
    }
}