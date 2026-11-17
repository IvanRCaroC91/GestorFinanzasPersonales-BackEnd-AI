package com.smartkash.identidad.verificacion.adaptadores.correo;

import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Objects;

/**
 * Envía el correo de verificación por SMTP.
 *
 * <p>Qué es SMTP: el protocolo estándar con el que se entregan los correos a un servidor de
 * correo. En local, ese servidor es Mailpit, que los guarda y los enseña en una página web sin
 * entregarlos a nadie; más adelante será un proveedor real. Para este adaptador da igual: solo
 * cambian la dirección y el puerto del servidor.</p>
 *
 * <p>Qué es: el adaptador del puerto {@link EnviadorCorreos}. Arma el mensaje y lo entrega con
 * {@code JavaMailSender}, el cliente de correo de Spring, que Spring Boot configura con las
 * propiedades {@code spring.mail.*}.</p>
 *
 * <p>Reglas:</p>
 * <ul>
 *   <li>El mensaje es texto plano en UTF-8. Sin HTML no hay forma de que el nombre de la
 *       persona, que escribe ella misma, se interprete como código.</li>
 *   <li>El asunto es fijo y el nombre solo va en el cuerpo, así que nada de lo que escribe la
 *       persona puede añadir cabeceras al mensaje.</li>
 *   <li>Ni el destinatario ni el enlace (que lleva el token) se escriben en los registros. Por
 *       eso los errores se relanzan con un mensaje propio, sin la causa original, que podría
 *       incluir la dirección.</li>
 * </ul>
 */
class EnviadorCorreosSmtp implements EnviadorCorreos {

    private static final Logger LOG = LoggerFactory.getLogger(EnviadorCorreosSmtp.class);

    /** Asunto de todos los correos de verificación. */
    static final String ASUNTO = "Confirma tu correo en SmartKash";

    private final JavaMailSender cliente;
    private final String remitente;

    /**
     * @param cliente   el cliente SMTP
     * @param remitente dirección que aparece como remitente, ya comprobada
     */
    EnviadorCorreosSmtp(JavaMailSender cliente, String remitente) {
        this.cliente = Objects.requireNonNull(cliente);
        this.remitente = Objects.requireNonNull(remitente);
    }

    /**
     * Arma y envía el correo de verificación.
     *
     * @param correo a quién, con qué nombre y con qué enlace
     * @throws MailPreparationException si el destinatario no es una dirección válida
     * @throws MailSendException        si el servidor de correo no acepta el mensaje
     */
    @Override
    public void enviarVerificacion(CorreoDeVerificacion correo) {
        MimeMessage mensaje = cliente.createMimeMessage();
        try {
            MimeMessageHelper ayudante = new MimeMessageHelper(mensaje, false, "UTF-8");
            ayudante.setFrom(remitente);
            // El segundo argumento pide una comprobación estricta de la dirección.
            ayudante.setTo(new InternetAddress(correo.destinatario(), true));
            ayudante.setSubject(ASUNTO);
            ayudante.setText(cuerpo(correo), false);
        } catch (MessagingException error) {
            LOG.warn("No se pudo preparar un correo de verificación: {}", error.getClass().getSimpleName());
            throw new MailPreparationException("No se pudo preparar el correo de verificación.");
        }

        try {
            cliente.send(mensaje);
        } catch (MailException error) {
            LOG.warn("No se pudo enviar un correo de verificación: {}", error.getClass().getSimpleName());
            throw new MailSendException("No se pudo enviar el correo de verificación.");
        }
    }

    /* Texto del mensaje: saludo, enlace, validez y qué hacer si no se pidió. */
    private static String cuerpo(CorreoDeVerificacion correo) {
        long horas = TokenVerificacion.VIGENCIA.toHours();
        return "Hola, " + correo.nombre() + ":\n\n"
                + "Para confirmar tu correo en SmartKash, abre este enlace:\n\n"
                + correo.enlace() + "\n\n"
                + "El enlace vale " + horas + " horas y solo se puede usar una vez.\n\n"
                + "Si no creaste una cuenta en SmartKash, ignora este mensaje.\n";
    }
}
