package com.smartkash.identidad.registro.puertos;

/**
 * Convierte una contraseña en un hash apto para guardarse.
 *
 * <p>Qué es: un puerto. Un hash es el resultado de una transformación de un solo
 * sentido: a partir de la contraseña se obtiene el hash, pero del hash no se puede
 * recuperar la contraseña. Así, aunque alguien lea la base de datos, no obtiene las
 * contraseñas. El algoritmo concreto lo decide la clase que implemente esta interfaz.</p>
 */
@FunctionalInterface
public interface HasheadorContrasena {

    /**
     * Calcula el hash de una contraseña.
     *
     * <p>Reglas: la contraseña se usa tal como llega, sin recortarla ni transformarla.
     * La implementación no debe escribirla en logs ni guardarla. Dos llamadas con la
     * misma contraseña pueden devolver hashes distintos, porque los algoritmos seguros
     * añaden un dato aleatorio en cada cálculo.</p>
     *
     * @param contrasena la contraseña en claro, ya validada
     * @return el hash, que incluye los parámetros necesarios para verificarlo después
     */
    String hashear(String contrasena);
}
