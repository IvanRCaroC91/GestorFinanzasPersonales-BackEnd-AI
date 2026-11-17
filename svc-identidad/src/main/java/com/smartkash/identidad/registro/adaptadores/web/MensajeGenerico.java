package com.smartkash.identidad.registro.adaptadores.web;

/**
 * Respuesta con un único mensaje que no revela información.
 *
 * <p>Qué es: el cuerpo que se devuelve cuando lo importante es no dar pistas, como en el
 * registro: el texto es el mismo se haya creado la cuenta o no. Spring lo convierte en el
 * JSON {@code {"mensaje": "..."}}.</p>
 *
 * @param mensaje texto para la persona; nunca debe incluir datos de la solicitud
 */
record MensajeGenerico(String mensaje) {
}
