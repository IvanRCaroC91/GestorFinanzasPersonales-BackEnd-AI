package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * Error del inicio de sesión cuando hay un bloqueo en curso por demasiados fallos.
 *
 * <p>Cuándo se lanza: antes de mirar la contraseña, si la cuenta o la dirección IP desde la
 * que llega el intento tienen un bloqueo que todavía no ha terminado. Se lanza aunque la
 * contraseña sea la correcta: el bloqueo existe precisamente para no dejar probar más.</p>
 *
 * <p>Qué lleva: solo el tiempo que falta para poder intentarlo de nuevo. No dice si el
 * bloqueo es de la cuenta o de la IP.</p>
 *
 * <p>A dónde va: la capa web lo convierte en una respuesta {@code 429} con la cabecera
 * {@code Retry-After}, que indica los segundos de espera.</p>
 */
public final class DemasiadosIntentos extends RuntimeException {

    private final Duration espera;

    /**
     * Crea el error con el tiempo de espera.
     *
     * @param espera cuánto falta para que termine el bloqueo
     * @throws NullPointerException si falta la espera
     */
    public DemasiadosIntentos(Duration espera) {
        super("Demasiados intentos fallidos. Espera antes de volver a intentarlo.");
        this.espera = Objects.requireNonNull(espera, "La espera es obligatoria.");
    }

    /**
     * Devuelve cuánto falta para poder intentarlo de nuevo.
     *
     * @return el tiempo de espera restante
     */
    public Duration espera() {
        return espera;
    }
}
