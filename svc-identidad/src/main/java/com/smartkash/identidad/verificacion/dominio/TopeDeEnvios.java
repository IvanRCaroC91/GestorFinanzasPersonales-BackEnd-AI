package com.smartkash.identidad.verificacion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tope de enlaces de verificación que una persona puede pedir: {@value #MAXIMO} por hora.
 *
 * <p>Por qué existe: sin tope, alguien con una sesión iniciada podría usar el servicio para
 * llenar de correos un buzón, o gastar la cuota del servidor de correo. La ventana es móvil:
 * se cuentan los enlaces emitidos en la hora anterior al momento de la petición.</p>
 *
 * <p>No guarda nada: quien llama obtiene los enlaces emitidos desde {@link #inicioDeLaVentana}
 * y pregunta con {@link #admiteOtro} y {@link #esperaHastaOtro}.</p>
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

    /**
     * Dice cuánto falta para poder pedir otro enlace.
     *
     * <p>Cómo se calcula: con la ventana móvil, se puede pedir otro en cuanto en la última hora
     * queden menos de {@value #MAXIMO}. Si hay {@code n} enlaces en la ventana, ordenados del más
     * antiguo al más reciente, el que tiene que salir es el que ocupa la posición
     * {@code n - MAXIMO}; sale una hora después de emitirse.</p>
     *
     * @param emitidosEnLaVentana fechas de emisión de los enlaces de la última hora, en cualquier
     *                            orden
     * @param ahora               momento de la petición
     * @return el tiempo que falta, o cero si se puede pedir ya
     */
    public static Duration esperaHastaOtro(List<Instant> emitidosEnLaVentana, Instant ahora) {
        if (admiteOtro(emitidosEnLaVentana.size())) {
            return Duration.ZERO;
        }
        List<Instant> ordenados = emitidosEnLaVentana.stream().sorted().toList();
        Instant debeSalir = ordenados.get(ordenados.size() - MAXIMO);
        Duration espera = Duration.between(ahora, debeSalir.plus(VENTANA));
        return espera.isNegative() ? Duration.ZERO : espera;
    }
}