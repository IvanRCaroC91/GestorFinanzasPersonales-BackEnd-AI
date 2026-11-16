package com.smartkash.nucleo.web.errores;

/**
 * Problema encontrado en un campo concreto de una solicitud.
 *
 * <p>Qué es: un elemento de la lista {@code errores} que acompaña a una respuesta de error de
 * datos de entrada. Con él, la pantalla puede mostrar el mensaje junto al campo que falló.</p>
 *
 * <p>Regla: el mensaje explica qué se esperaba, pero nunca repite el valor que envió la
 * persona, porque podría ser un dato sensible, como una contraseña.</p>
 *
 * @param campo   nombre del campo tal como aparece en el cuerpo de la solicitud
 * @param mensaje explicación para la persona
 */
public record ErrorCampo(String campo, String mensaje) {
}
