package com.smartkash.identidad.verificacion.puertos;

import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;

/**
 * Envía correos a las personas.
 *
 * <p>Qué es: un puerto. El caso de uso decide qué se envía y a quién; cómo se entrega (SMTP a
 * un buzón de pruebas en local, un proveedor real más adelante) lo decide la clase que
 * implemente esta interfaz.</p>
 */
@FunctionalInterface
public interface EnviadorCorreos {

    /**
     * Envía el correo con el enlace de verificación.
     *
     * <p>Reglas: no escribe en los registros el destinatario ni el enlace, que lleva el token
     * en claro.</p>
     *
     * @param correo a quién, con qué nombre y con qué enlace
     * @throws RuntimeException si el servidor de correo no acepta el mensaje; quien llama decide
     *                          si eso afecta a la respuesta
     */
    void enviarVerificacion(CorreoDeVerificacion correo);
}