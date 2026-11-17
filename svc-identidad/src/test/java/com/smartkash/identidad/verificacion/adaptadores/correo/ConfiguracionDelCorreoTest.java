package com.smartkash.identidad.verificacion.adaptadores.correo;

import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el servicio no arranca sin servidor de correo ni remitente.
 *
 * <p>Qué hace: arranca solo la configuración del correo y la configuración automática de
 * Spring Boot que crea el cliente SMTP, con las propiedades que indica cada prueba. No conecta
 * con ningún servidor: el cliente solo se conecta al enviar.</p>
 */
class ConfiguracionDelCorreoTest {

    private final ApplicationContextRunner arranque = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(ConfiguracionDelCorreo.class);

    /** Con servidor y remitente, arranca y ofrece el enviador de correos. */
    @Test
    void con_servidor_y_remitente_arranca() {
        arranque.withPropertyValues("spring.mail.host=127.0.0.1", "spring.mail.port=1025",
                        "identidad.correo.remitente=no-responder@pruebas.invalid")
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto).hasSingleBean(EnviadorCorreos.class);
                });
    }

    /** Con el servidor vacío (la variable no está definida), no arranca y dice qué variable falta. */
    @Test
    void sin_servidor_no_arranca() {
        arranque.withPropertyValues("spring.mail.host=",
                        "identidad.correo.remitente=no-responder@pruebas.invalid")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("IDENTIDAD_SMTP_HOST");
                });
    }

    /** Sin remitente, no arranca y dice qué variable falta. */
    @Test
    void sin_remitente_no_arranca() {
        arranque.withPropertyValues("spring.mail.host=127.0.0.1")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("IDENTIDAD_CORREO_REMITENTE");
                });
    }

    /** Con un remitente que no es una dirección de correo, no arranca. */
    @Test
    void con_un_remitente_mal_formado_no_arranca() {
        arranque.withPropertyValues("spring.mail.host=127.0.0.1",
                        "identidad.correo.remitente=esto no es un correo")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("IDENTIDAD_CORREO_REMITENTE");
                });
    }
}