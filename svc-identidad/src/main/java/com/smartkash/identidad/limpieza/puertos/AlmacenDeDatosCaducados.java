package com.smartkash.identidad.limpieza.puertos;

import java.time.Instant;

/**
 * Borra los datos que ya cumplieron su tiempo de conservación.
 *
 * <p>Qué es: el puerto de la limpieza. El caso de uso decide las fechas límite (cuánto tiempo
 * se conserva cada dato); la implementación solo sabe borrar lo anterior a una fecha. Cada
 * método borra lo estrictamente anterior al límite: lo que coincide con él se conserva.</p>
 *
 * <p>Reglas para quien lo implemente: borrar no debe bloquear las tablas para el resto del
 * servicio (por ejemplo, borrando por lotes pequeños) y repetir una limpieza no debe hacer
 * daño: si ya no queda nada que borrar, se devuelve cero.</p>
 */
public interface AlmacenDeDatosCaducados {

    /**
     * Borra los contadores de intentos fallidos cuya última actividad registrada (el inicio
     * de su ventana o el fin de su bloqueo, la más reciente) es anterior al límite.
     *
     * @param limite el instante a partir del cual un contador se conserva
     * @return cuántos contadores se borraron
     */
    long borrarContadoresSinActividadDesde(Instant limite);

    /**
     * Borra los tokens de renovación que caducaron antes del límite, se hayan usado o no.
     *
     * @param limite el instante a partir del cual un token se conserva
     * @return cuántos tokens se borraron
     */
    long borrarTokensDeRenovacionCaducadosAntesDe(Instant limite);

    /**
     * Borra los enlaces de un solo uso (verificación del correo y recuperación de la
     * contraseña) que caducaron antes del límite, se hayan usado o no.
     *
     * @param limite el instante a partir del cual un enlace se conserva
     * @return cuántos enlaces se borraron
     */
    long borrarEnlacesDeUnSoloUsoCaducadosAntesDe(Instant limite);
}
