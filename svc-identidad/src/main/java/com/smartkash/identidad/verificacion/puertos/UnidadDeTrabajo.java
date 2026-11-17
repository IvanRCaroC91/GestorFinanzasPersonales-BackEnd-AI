package com.smartkash.identidad.verificacion.puertos;

/**
 * Ejecuta un bloque de trabajo como una sola transacción.
 *
 * <p>Qué es una transacción: un grupo de cambios en la base de datos que se guardan todos o
 * ninguno. Por ejemplo, al verificar un correo se marca el enlace como usado y se activa la
 * cuenta; si lo segundo fallara, lo primero se deshace y nada queda a medias.</p>
 *
 * <p>Qué es: un puerto. Los casos de uso no usan Spring, así que no pueden anotar sus métodos
 * con {@code @Transactional}; en su lugar le pasan el trabajo a esta interfaz. Cómo se abre y se
 * cierra la transacción lo decide la clase que la implementa.</p>
 */
@FunctionalInterface
public interface UnidadDeTrabajo {

    /**
     * Ejecuta el trabajo dentro de una transacción.
     *
     * <p>Si el trabajo termina bien, se guardan todos sus cambios. Si lanza una excepción, se
     * deshacen todos y la excepción sale tal cual hacia quien llamó.</p>
     *
     * @param trabajo lo que hay que hacer
     */
    void ejecutar(Runnable trabajo);
}
