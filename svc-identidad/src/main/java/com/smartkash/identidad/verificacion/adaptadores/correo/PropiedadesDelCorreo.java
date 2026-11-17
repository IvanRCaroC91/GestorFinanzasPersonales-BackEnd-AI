package com.smartkash.identidad.verificacion.adaptadores.correo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia del envío de correos, leída de {@code application.yml}.
 *
 * <p>El servidor y el puerto SMTP no están aquí: los lee Spring Boot de
 * {@code spring.mail.host} y {@code spring.mail.port} para crear el cliente de correo.</p>
 *
 * @param remitente dirección que aparece como remitente; viene de
 *                  {@code IDENTIDAD_CORREO_REMITENTE}
 */
@ConfigurationProperties("identidad.correo")
record PropiedadesDelCorreo(String remitente) {
}
