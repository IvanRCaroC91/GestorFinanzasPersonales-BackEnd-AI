package com.smartkash.identidad.cuenta.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link CorreoNormalizado}.
 *
 * <p>Qué comprueban: que el correo que escribe la persona se convierte siempre en una
 * única forma (sin espacios en los extremos y en minúsculas), de modo que dos formas de
 * escribir el mismo correo se consideren la misma cuenta. También comprueban que se
 * rechazan los valores vacíos y los que superan el largo máximo de un correo (254
 * caracteres).</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos, por eso son
 * rápidas.</p>
 */
class CorreoNormalizadoTest {

    /** Sufijo fijo de 11 caracteres para construir correos de un largo exacto. */
    private static final String DOMINIO = "@correo.com";

    @Test
    @DisplayName("quita los espacios de los extremos y pasa a minúsculas")
    void normalizaEspaciosYMayusculas() {
        CorreoNormalizado correo = new CorreoNormalizado("  Ana@Correo.COM ");

        assertEquals("ana@correo.com", correo.valor());
    }

    @Test
    @DisplayName("dos formas de escribir el mismo correo son iguales")
    void variantesDelMismoCorreoSonIguales() {
        CorreoNormalizado escritoConMayusculas = new CorreoNormalizado("ANA@CORREO.COM");
        CorreoNormalizado escritoConEspacios = new CorreoNormalizado("  ana@correo.com  ");

        assertEquals(escritoConMayusculas, escritoConEspacios);
    }

    @Test
    @DisplayName("no depende del idioma configurado en la máquina")
    void noDependeDelIdiomaDelSistema() {
        // En turco, la "I" mayúscula se convierte en una "ı" sin punto. Si la clase usara
        // el idioma del sistema, el mismo correo quedaría guardado de dos formas distintas.
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            CorreoNormalizado correo = new CorreoNormalizado("TITO@CORREO.COM");

            assertEquals("tito@correo.com", correo.valor());
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", " \n "})
    @DisplayName("rechaza un correo nulo, vacío o hecho solo de espacios")
    void rechazaNuloOVacio(String valor) {
        assertThrows(IllegalArgumentException.class, () -> new CorreoNormalizado(valor));
    }

    @Test
    @DisplayName("acepta un correo de exactamente 254 caracteres")
    void aceptaElLargoMaximo() {
        String correo254 = "a".repeat(254 - DOMINIO.length()) + DOMINIO;

        assertDoesNotThrow(() -> new CorreoNormalizado(correo254));
    }

    @Test
    @DisplayName("rechaza un correo de 255 caracteres")
    void rechazaUnCaracterDeMas() {
        String correo255 = "a".repeat(255 - DOMINIO.length()) + DOMINIO;

        assertThrows(IllegalArgumentException.class, () -> new CorreoNormalizado(correo255));
    }

    @Test
    @DisplayName("el largo se mide después de quitar los espacios de los extremos")
    void elLargoSeMideSinEspaciosExtremos() {
        String correo254ConEspacios = "  " + "a".repeat(254 - DOMINIO.length()) + DOMINIO + "  ";

        CorreoNormalizado correo = new CorreoNormalizado(correo254ConEspacios);

        assertEquals(254, correo.valor().length());
    }

    @Test
    @DisplayName("el texto de depuración no muestra el correo")
    void elTextoDeDepuracionNoMuestraElCorreo() {
        String texto = new CorreoNormalizado("ana.secreta@correo.com").toString();

        assertFalse(texto.contains("ana.secreta"));
        assertFalse(texto.contains("correo.com"));
    }
}