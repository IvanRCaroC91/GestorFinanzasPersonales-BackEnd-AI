package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Contador de inicios de sesión fallidos de una cuenta o de una dirección IP.
 *
 * <p>Qué hace: recuerda cuántos fallos seguidos hubo, cuándo empezaron y, si se alcanzó el
 * límite, hasta cuándo dura el bloqueo. Con eso responde a dos preguntas: "¿hay que esperar
 * antes de intentarlo?" y "¿cómo queda el contador después de este fallo?".</p>
 *
 * <p>Qué es: un record inmutable. Anotar un fallo no cambia este objeto: devuelve otro
 * nuevo con la cuenta actualizada. Así no puede quedar a medio modificar.</p>
 *
 * <p>De dónde vienen los datos: de la tabla de contadores de acceso, donde hay una fila por
 * cada cuenta o IP con fallos recientes. A dónde van: de vuelta a esa tabla. Esta clase no
 * sabe a quién pertenece el contador ni cómo se guarda; solo hace las cuentas.</p>
 *
 * <p>No mira ningún reloj: la hora se le pasa en cada llamada. Por eso sus reglas se pueden
 * probar simulando el paso del tiempo.</p>
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>Los fallos se acumulan dentro de una ventana de tiempo que empieza con el primero.</li>
 *   <li>El fallo que alcanza el límite bloquea durante 1 minuto. Cada fallo posterior
 *       duplica la espera, hasta un tope de 15 minutos (ver {@link EsperaCreciente}).</li>
 *   <li>Mientras dura un bloqueo, los intentos se rechazan sin comprobar la contraseña, así
 *       que no son fallos y no se anotan aquí.</li>
 *   <li>El contador vuelve a empezar cuando pasa una ventana completa sin fallos: contada
 *       desde el primer fallo si nunca hubo bloqueo, o desde el final del último bloqueo si
 *       lo hubo. Sin esta segunda parte, quien insiste vería su contador reiniciarse en
 *       pleno ataque y la espera nunca llegaría al tope.</li>
 * </ul>
 *
 * @param fallos         fallos acumulados; al menos uno, porque el contador nace con el
 *                       primer fallo
 * @param ventanaInicio  momento del primer fallo de la cuenta en curso
 * @param bloqueadoHasta momento en que termina el bloqueo, o {@code null} si todavía no se
 *                       alcanzó el límite
 */
public record ControlDeIntentos(int fallos, Instant ventanaInicio, Instant bloqueadoHasta) {

    /**
     * Valor en el que el contador deja de crecer. A partir de unos pocos fallos por encima
     * del límite la espera ya es la máxima, así que seguir sumando no cambia nada; el tope
     * evita que el número crezca sin fin si alguien insiste durante meses.
     */
    public static final int MAXIMO_FALLOS = 1_000;

    /**
     * Comprueba que el contador tiene sentido al crearlo.
     *
     * @throws NullPointerException     si falta el inicio de la ventana
     * @throws IllegalArgumentException si no hay al menos un fallo
     */
    public ControlDeIntentos {
        Objects.requireNonNull(ventanaInicio, "El inicio de la ventana es obligatorio.");
        if (fallos < 1) {
            throw new IllegalArgumentException("Un contador tiene al menos un fallo.");
        }
    }

    /**
     * Crea el contador de una cuenta o IP que falla por primera vez.
     *
     * @param ahora  momento del fallo
     * @param limite cuántos fallos se toleran y en cuánto tiempo
     * @return un contador con un fallo y la ventana abierta en ese momento
     */
    public static ControlDeIntentos trasPrimerFallo(Instant ahora, LimiteDeIntentos limite) {
        return con(1, ahora, ahora, limite);
    }

    /**
     * Devuelve cómo queda el contador después de un fallo más.
     *
     * <p>Si ya pasó una ventana completa sin fallos, el anterior se olvida y este cuenta
     * como el primero. Si no, se suma y, cuando se alcanza o se supera el límite, empieza un
     * bloqueo que se mide desde este fallo.</p>
     *
     * @param ahora  momento del fallo
     * @param limite cuántos fallos se toleran y en cuánto tiempo
     * @return el contador actualizado; este objeto no cambia
     */
    public ControlDeIntentos trasOtroFallo(Instant ahora, LimiteDeIntentos limite) {
        if (caducado(ahora, limite)) {
            return trasPrimerFallo(ahora, limite);
        }
        return con(Math.min(fallos + 1, MAXIMO_FALLOS), ventanaInicio, ahora, limite);
    }

    /**
     * Dice cuánto falta para poder intentarlo de nuevo.
     *
     * @param ahora momento en que llega el intento
     * @return el tiempo que falta para que termine el bloqueo, o una caja vacía si no hay
     *         bloqueo o ya terminó
     */
    public Optional<Duration> esperaRestante(Instant ahora) {
        if (bloqueadoHasta == null || !ahora.isBefore(bloqueadoHasta)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, bloqueadoHasta));
    }

    /**
     * Dice si el contador ya no cuenta: pasó una ventana completa sin fallos.
     *
     * <p>Sirve para decidir que el siguiente fallo empieza de cero y para saber qué filas
     * de la tabla se pueden borrar.</p>
     *
     * @param ahora  momento de la consulta
     * @param limite el límite con el que se lleva este contador
     * @return {@code true} si ya pasó la ventana, contada desde el primer fallo o, si hubo
     *         bloqueo, desde que terminó
     */
    public boolean caducado(Instant ahora, LimiteDeIntentos limite) {
        Instant desde = bloqueadoHasta != null ? bloqueadoHasta : ventanaInicio;
        return !ahora.isBefore(desde.plus(limite.ventana()));
    }

    /* Arma el contador y, si los fallos alcanzan el límite, calcula hasta cuándo se bloquea. */
    private static ControlDeIntentos con(
            int fallos, Instant ventanaInicio, Instant ahora, LimiteDeIntentos limite) {
        int fallosDeMas = fallos - limite.fallosPermitidos();
        Instant bloqueadoHasta = fallosDeMas < 0 ? null : ahora.plus(EsperaCreciente.tras(fallosDeMas));
        return new ControlDeIntentos(fallos, ventanaInicio, bloqueadoHasta);
    }
}