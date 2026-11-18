package com.smartkash.identidad.limpieza.aplicacion;

/**
 * Cuántas filas borró una limpieza, de cada tipo.
 *
 * <p>Para qué sirve: dejar constancia en el log de lo que hizo cada ejecución. Si un día los
 * contadores borrados se disparan, es señal de que alguien probó muchas contraseñas el día
 * anterior.</p>
 *
 * @param contadores         contadores de intentos fallidos borrados
 * @param tokensDeRenovacion tokens de renovación borrados
 * @param enlacesDeUnSoloUso enlaces de verificación o recuperación borrados
 */
public record ResultadoDeLimpieza(long contadores, long tokensDeRenovacion, long enlacesDeUnSoloUso) {

    /**
     * Suma las filas borradas de todos los tipos.
     *
     * @return el total de filas borradas
     */
    public long total() {
        return contadores + tokensDeRenovacion + enlacesDeUnSoloUso;
    }
}
