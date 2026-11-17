package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;
import java.util.Objects;

/**
 * Cuántos inicios de sesión fallidos se toleran, y en cuánto tiempo, antes de bloquear.
 *
 * <p>Qué es: un record, es decir, una clase que solo agrupa datos y no cambia después de
 * crearse. Aquí agrupa las dos cifras de un límite. Por ejemplo, "5 fallos en 15 minutos".</p>
 *
 * <p>Para qué sirve: los fallos se cuentan por separado para cada cuenta y para cada
 * dirección IP, con un límite distinto. El de la cuenta frena a quien prueba muchas
 * contraseñas contra una misma persona; el de la IP, más holgado, frena a quien prueba pocas
 * contraseñas contra muchas cuentas desde un mismo sitio. Las reglas de cálculo son las
 * mismas para los dos; solo cambia el límite que se les pasa.</p>
 *
 * <p>De dónde vienen los datos: son valores fijos, decididos en la especificación. A dónde
 * van: los usa {@link ControlDeIntentos} cada vez que anota un fallo.</p>
 *
 * @param fallosPermitidos fallos que provocan el bloqueo; el fallo que hace ese número es el
 *                         primero que bloquea
 * @param ventana          tiempo durante el cual se acumulan los fallos; pasado ese tiempo
 *                         sin llegar al límite, la cuenta vuelve a empezar
 */
public record LimiteDeIntentos(int fallosPermitidos, Duration ventana) {

    /** Límite para los fallos contra una misma cuenta: 5 en 15 minutos. */
    public static final LimiteDeIntentos POR_CUENTA = new LimiteDeIntentos(5, Duration.ofMinutes(15));

    /** Límite para los fallos desde una misma dirección IP: 20 en 15 minutos. */
    public static final LimiteDeIntentos POR_IP = new LimiteDeIntentos(20, Duration.ofMinutes(15));

    /**
     * Comprueba que el límite tiene sentido al crearlo.
     *
     * @throws NullPointerException     si falta la ventana
     * @throws IllegalArgumentException si no se tolera al menos un fallo o la ventana no es
     *                                  mayor que cero
     */
    public LimiteDeIntentos {
        Objects.requireNonNull(ventana, "La ventana es obligatoria.");
        if (fallosPermitidos < 1) {
            throw new IllegalArgumentException("El límite debe ser de al menos un fallo.");
        }
        if (ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("La ventana debe ser mayor que cero.");
        }
    }
}