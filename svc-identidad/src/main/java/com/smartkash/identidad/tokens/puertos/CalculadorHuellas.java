package com.smartkash.identidad.tokens.puertos;

/**
 * Calcula la huella de un texto: un resumen de tamaño fijo del que no se puede recuperar el
 * texto original.
 *
 * <p>Qué es: un puerto. Los casos de uso piden la huella sin saber con qué algoritmo se
 * calcula.</p>
 *
 * <p>Para qué sirve: para guardar en la base de datos algo que permita reconocer un valor
 * sin guardar el valor. Se usa con los tokens (si alguien lee la tabla, no obtiene tokens
 * que pueda usar) y con las claves de los contadores de acceso (el correo y la dirección IP
 * no quedan escritos en claro).</p>
 *
 * <p>En qué se diferencia del hash de una contraseña: aquel es lento a propósito, porque
 * las contraseñas se pueden adivinar probando. Esta huella es rápida, y es suficiente para
 * valores aleatorios largos, que no se pueden adivinar.</p>
 */
@FunctionalInterface
public interface CalculadorHuellas {

    /**
     * Calcula la huella de un texto.
     *
     * <p>Reglas: el mismo texto da siempre la misma huella, y la huella mide siempre 64
     * caracteres: cifras y letras de la a a la f, en minúsculas.</p>
     *
     * @param texto el texto del que se quiere la huella; no se guarda ni se escribe en logs
     * @return la huella, de 64 caracteres
     * @throws NullPointerException si el texto es nulo
     */
    String huellaDe(String texto);
}