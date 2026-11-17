package com.smartkash.identidad.sesion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link TokenAcceso}.
 *
 * <p>Qué comprueban: que el token de acceso lleva su valor y su caducidad, que su vigencia
 * acordada es de 15 minutos y que su valor no puede acabar escrito en un log por descuido.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class TokenAccesoTest {

    private static final Instant CADUCIDAD = Instant.parse("2026-11-16T10:15:00Z");
    private static final String VALOR = "cabecera.datos.firma";

    @Test
    @DisplayName("la vigencia acordada del token de acceso es de 15 minutos")
    void laVigenciaEsDeQuinceMinutos() {
        assertEquals(Duration.ofMinutes(15), TokenAcceso.VIGENCIA);
    }

    @Test
    @DisplayName("conserva su valor y su caducidad")
    void conservaSusDatos() {
        TokenAcceso token = new TokenAcceso(VALOR, CADUCIDAD);

        assertEquals(VALOR, token.valor());
        assertEquals(CADUCIDAD, token.expiraEn());
    }

    @Test
    @DisplayName("exige un valor no vacío y una caducidad")
    void exigeDatosCoherentes() {
        assertThrows(NullPointerException.class, () -> new TokenAcceso(null, CADUCIDAD));
        assertThrows(NullPointerException.class, () -> new TokenAcceso(VALOR, null));
        assertThrows(IllegalArgumentException.class, () -> new TokenAcceso("  ", CADUCIDAD));
    }

    @Test
    @DisplayName("el texto de depuración no muestra el valor del token")
    void elTextoDeDepuracionNoRevelaElValor() {
        String texto = new TokenAcceso(VALOR, CADUCIDAD).toString();

        assertFalse(texto.contains(VALOR));
        assertFalse(texto.contains("firma"));
    }
}