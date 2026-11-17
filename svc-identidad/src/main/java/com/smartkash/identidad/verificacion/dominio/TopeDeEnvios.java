package com.smartkash.identidad.verificacion.dominio;

import java.time.Duration;
import java.time.Instant;

/**
 * Tope de enlaces de verificación que una persona puede pedir: {@value #MAXIMO} por hora.
 *
 * <p>Por qué existe: sin tope, alguien con una sesión iniciada podría usar el servicio para
 * llenar de correos un buzón, o gastar la cuota del servidor de correo. La ventana es móvil:
 * se cuentan los enlaces emitidos en la hora anterior al momento de la petición.</p>
 *
 * <p>No guarda nada: quien llama cuenta los enlaces emitidos desde {@link #inicioDeLaVentana}
 * y pregunta con {@link #admiteOtro}.</p>
 */
public final class TopeDeEnvios {

    /** Enlaces que se admiten dentro de la ventana. */
    public static final int MAXIMO = 3;

    /** Tamaño de la ventana de tiempo en la que se cuentan los enlaces. */
    public static final Duration VENTANA = Duration.ofHours(1);

    private TopeDeEnvios() {
    }

    /**
     * Dice desde cuándo hay que contar los enlaces emitidos.
     *
     * @param ahora momento de la petición
     * @return una hora antes
     */
    public static Instant inicioDeLaVentana(Instant ahora) {
        return ahora.minus(VENTANA);
    }

    /**
     * Dice si se puede emitir otro enlace.
     *
     * @param emitidosEnLaVentana enlaces emitidos desde {@link #inicioDeLaVentana}
     * @return {@code true} si todavía no se alcanzó el tope
     */
    public static boolean admiteOtro(int emitidosEnLaVentana) {
        return emitidosEnLaVentana < MAXIMO;
    }
}