package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.identidad.ClavesDePrueba;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el servicio no arranca sin una clave de firma y un emisor válidos.
 *
 * <p>Qué hace: usa {@code ApplicationContextRunner}, una herramienta de Spring Boot que
 * arranca solo las clases de configuración que se le indican, con las propiedades que se le
 * pasan, sin base de datos ni servidor web. Si la configuración lanza un error al crear sus
 * beans, el arranque falla, igual que fallaría el servicio real.</p>
 */
class ConfiguracionDeLaFirmaTest {

    private static final Path CLAVE_VALIDA = ClavesDePrueba.archivoTemporal(
            ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate()));

    private final ApplicationContextRunner arranque = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionDeLaFirma.class);

    /** Con clave y emisor, arranca y ofrece el emisor de tokens de acceso. */
    @Test
    void con_clave_y_emisor_arranca() {
        arranque.withPropertyValues(
                        "identidad.token.clave-firma-ruta=" + CLAVE_VALIDA,
                        "identidad.token.emisor=https://identidad.pruebas.invalid")
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed();
                    assertThat(contexto).hasSingleBean(EmisorTokenAcceso.class);
                });
    }

    /** Sin la ruta de la clave, no arranca y el error dice qué variable falta. */
    @Test
    void sin_la_ruta_de_la_clave_no_arranca() {
        arranque.withPropertyValues("identidad.token.emisor=https://identidad.pruebas.invalid")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("IDENTIDAD_CLAVE_FIRMA_RUTA");
                });
    }

    /** Sin el emisor, no arranca y el error dice qué variable falta. */
    @Test
    void sin_el_emisor_no_arranca() {
        arranque.withPropertyValues("identidad.token.clave-firma-ruta=" + CLAVE_VALIDA)
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause()
                            .hasMessageContaining("IDENTIDAD_TOKEN_EMISOR");
                });
    }

    /** Con una clave demasiado corta, no arranca. */
    @Test
    void con_una_clave_corta_no_arranca() {
        Path corta = ClavesDePrueba.archivoTemporal(
                ClavesDePrueba.pem(ClavesDePrueba.rsa(2048).getPrivate()));

        arranque.withPropertyValues(
                        "identidad.token.clave-firma-ruta=" + corta,
                        "identidad.token.emisor=https://identidad.pruebas.invalid")
                .run(contexto -> {
                    assertThat(contexto).hasFailed();
                    assertThat(contexto.getStartupFailure()).rootCause().hasMessageContaining("3072");
                });
    }
}
