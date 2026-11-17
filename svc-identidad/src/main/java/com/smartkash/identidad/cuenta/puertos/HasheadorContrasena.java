package com.smartkash.identidad.cuenta.puertos;

/**
 * Convierte una contraseña en un hash apto para guardarse y comprueba después si una
 * contraseña corresponde a un hash guardado.
 *
 * <p>Qué es: un puerto. Un hash es el resultado de una transformación de un solo
 * sentido: a partir de la contraseña se obtiene el hash, pero del hash no se puede
 * recuperar la contraseña. Así, aunque alguien lea la base de datos, no obtiene las
 * contraseñas. El algoritmo concreto lo decide la clase que implemente esta interfaz.</p>
 *
 * <p>Quién lo usa: el registro, para calcular el hash de la cuenta nueva, y el inicio de
 * sesión, para comparar lo que escribe la persona con el hash guardado.</p>
 */
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

    /**
     * Dice si una contraseña es la que dio origen a un hash.
     *
     * <p>Cómo funciona: como el hash no se puede deshacer, se repite el cálculo con la
     * contraseña recibida y con los mismos parámetros y el mismo dato aleatorio que lleva
     * escritos el hash, y se comparan los resultados. Por eso cuesta lo mismo que
     * {@link #hashear}: es lento a propósito.</p>
     *
     * <p>Reglas: la contraseña se usa tal como llega y no se escribe en logs. El tiempo
     * de la comparación no debe depender de cuántos caracteres coinciden.</p>
     *
     * @param contrasena la contraseña en claro que escribió la persona
     * @param hash       el hash guardado contra el que se compara
     * @return {@code true} si la contraseña corresponde al hash
     */
    boolean coincide(String contrasena, String hash);
}