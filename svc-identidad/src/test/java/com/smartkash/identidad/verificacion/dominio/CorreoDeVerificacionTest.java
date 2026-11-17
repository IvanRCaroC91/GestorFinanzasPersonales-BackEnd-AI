package com.smartkash.identidad.verificacion.dominio;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas del correo de verificación: a quién va y qué enlace lleva.
 */
class CorreoDeVerificacionTest {

    private static final String VALOR = "valor-del-token-de-prueba-1234567890abcdefg";
    private static final Usuario ANA = Usuario.nuevo("Ana", "Ana@Correo.com",
            new CorreoNormalizado("Ana@Correo.com"), "hash-cualquiera");

    @Test
    @DisplayName("va al correo de la cuenta, con su nombre")
    void vaAlCorreoDeLaCuenta() {
        CorreoDeVerificacion correo = CorreoDeVerificacion.para(ANA, "https://app.ejemplo", VALOR);

        assertThat(correo.destinatario()).isEqualTo("Ana@Correo.com");
        assertThat(correo.nombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("el enlace lleva el token detrás de #, para que no llegue al servidor de la página")
    void elEnlaceLlevaElTokenEnElFragmento() {
        CorreoDeVerificacion correo = CorreoDeVerificacion.para(ANA, "https://app.ejemplo", VALOR);

        assertThat(correo.enlace()).isEqualTo("https://app.ejemplo/verificar-correo#token=" + VALOR);
    }

    @Test
    @DisplayName("una barra final en la dirección base no produce una barra doble")
    void unaBarraFinalNoSeDuplica() {
        CorreoDeVerificacion correo = CorreoDeVerificacion.para(ANA, "https://app.ejemplo/", VALOR);

        assertThat(correo.enlace()).isEqualTo("https://app.ejemplo/verificar-correo#token=" + VALOR);
    }

    @ParameterizedTest(name = "rechaza la dirección base [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "app.ejemplo", "ftp://app.ejemplo"})
    void rechazaUnaDireccionBaseNoValida(String base) {
        assertThatThrownBy(() -> CorreoDeVerificacion.para(ANA, base, VALOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el texto de depuración no muestra el correo ni el enlace")
    void elTextoDeDepuracionNoMuestraDatos() {
        String texto = CorreoDeVerificacion.para(ANA, "https://app.ejemplo", VALOR).toString();

        assertThat(texto).doesNotContain("Ana@Correo.com").doesNotContain(VALOR);
    }
}
