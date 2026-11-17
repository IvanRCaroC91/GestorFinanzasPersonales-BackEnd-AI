package com.smartkash.identidad.tokens.puertos;

/**
 * Fabrica los valores secretos que el servicio entrega a una persona.
 *
 * <p>Qué es: un puerto. Los casos de uso piden "un token nuevo" sin saber cómo se obtiene.
 * Por ejemplo, el valor que viaja en la cookie de renovación o el que va dentro del enlace
 * de verificación del correo.</p>
 *
 * <p>Qué es un token opaco: un texto aleatorio que no significa nada por sí mismo. Solo
 * sirve porque el servicio guardó su huella y puede reconocerlo cuando se lo devuelven.</p>
 */
@FunctionalInterface
public interface GeneradorTokens {

    /**
     * Genera un valor nuevo, imposible de adivinar.
     *
     * <p>Reglas: el valor debe salir de una fuente de azar apta para seguridad y tener al
     * menos 256 bits. Solo usa caracteres que pueden viajar tal cual en una dirección web o
     * en una cookie. Quien lo recibe lo entrega a la persona y guarda únicamente su huella;
     * el valor no debe escribirse en logs ni guardarse.</p>
     *
     * @return el valor en claro, distinto en cada llamada
     */
    String generar();
}
