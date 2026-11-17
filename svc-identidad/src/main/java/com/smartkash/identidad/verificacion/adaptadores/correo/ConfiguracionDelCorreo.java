package com.smartkash.identidad.verificacion.adaptadores.correo;

import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Prepara al arrancar el envío de correos y comprueba que está configurado.
 *
 * <p>Regla: el servicio no arranca sin servidor de correo o sin un remitente válido. Sin ellos
 * nadie podría verificar su correo, y es mejor descubrirlo al arrancar que con el primer
 * registro. Los mensajes de error nombran la variable de entorno que hay que definir.</p>
 *
 * <p>No comprueba que el servidor responda: el cliente de correo solo se conecta al enviar.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDelCorreo.class)
class ConfiguracionDelCorreo {

    /**
     * Crea el enviador de correos.
     *
     * @param clientes    el cliente SMTP que crea Spring Boot a partir de {@code spring.mail.*},
     *                    si lo creó
     * @param propiedades la configuración propia del correo
     * @param entorno     la configuración del servicio, de la que se lee el servidor
     * @return el enviador, que implementa el puerto {@link EnviadorCorreos}
     * @throws IllegalStateException si falta el servidor o el remitente no es válido
     */
    @Bean
    EnviadorCorreos enviadorCorreos(
            ObjectProvider<JavaMailSender> clientes, PropiedadesDelCorreo propiedades, Environment entorno) {

        String servidor = entorno.getProperty("spring.mail.host", "");
        JavaMailSender cliente = clientes.getIfAvailable();
        if (servidor.isBlank() || cliente == null) {
            throw new IllegalStateException("Falta el servidor de correo: define la variable de "
                    + "entorno IDENTIDAD_SMTP_HOST (y, si no es el 1025, IDENTIDAD_SMTP_PUERTO).");
        }
        return new EnviadorCorreosSmtp(cliente, remitenteValido(propiedades.remitente()));
    }

    /* El remitente debe ser una dirección de correo con forma estricta. */
    private static String remitenteValido(String remitente) {
        if (remitente == null || remitente.isBlank()) {
            throw new IllegalStateException("Falta el remitente de los correos: define la variable "
                    + "de entorno IDENTIDAD_CORREO_REMITENTE.");
        }
        try {
            new InternetAddress(remitente, true);
            return remitente;
        } catch (AddressException error) {
            throw new IllegalStateException("La variable de entorno IDENTIDAD_CORREO_REMITENTE no "
                    + "contiene una dirección de correo válida.");
        }
    }
}