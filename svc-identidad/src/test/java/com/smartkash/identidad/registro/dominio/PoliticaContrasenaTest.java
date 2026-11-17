package com.smartkash.identidad.registro.dominio;

import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;
import com.smartkash.identidad.registro.puertos.ContrasenasComunes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link PoliticaContrasena} y del error que produce, {@link ErrorDeRegistro}.
 *
 * <p>Qué comprueban: que una contraseña solo se acepta si tiene entre 12 y 64 caracteres,
 * no pesa más de 72 bytes en UTF-8, no es igual al correo de la persona y no está en la
 * lista de contraseñas comunes. También comprueban que la contraseña se evalúa tal como
 * se escribió (sin recortar espacios) y que el error indica el motivo y el campo sin
 * repetir la contraseña en el mensaje.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos. La lista de
 * contraseñas comunes se sustituye por una lista falsa de un solo elemento, definida
 * aquí mismo.</p>
 */
class PoliticaContrasenaTest {

    /**
     * Un emoji (cara sonriente). Es un solo carácter para la persona, pero ocupa 4 bytes
     * en UTF-8 y Java lo guarda internamente como dos unidades. Se escribe con códigos
     * de escape para que la prueba no dependa de la codificación del archivo.
     */
    private static final String EMOJI = "😀";

    /** Correo de la persona que se registra, ya normalizado. Tiene 14 caracteres. */
    private static final CorreoNormalizado CORREO = new CorreoNormalizado("ana@correo.com");

    /** Lista falsa de contraseñas comunes: solo contiene una, escrita en minúsculas. */
    private static final ContrasenasComunes COMUNES = Set.of("password1234")::contains;

    private final PoliticaContrasena politica = new PoliticaContrasena(COMUNES);

    // ---------- Largo en caracteres ----------

    @Test
    @DisplayName("rechaza una contraseña de 11 caracteres")
    void rechazaOnceCaracteres() {
        ErrorDeRegistro error = validarEsperandoError("a".repeat(11));

        assertEquals(Motivo.CONTRASENA_MUY_CORTA, error.motivo());
    }

    @Test
    @DisplayName("acepta una contraseña de exactamente 12 caracteres")
    void aceptaDoceCaracteres() {
        assertDoesNotThrow(() -> politica.validar("a".repeat(12), CORREO));
    }

    @Test
    @DisplayName("acepta una contraseña de exactamente 64 caracteres")
    void aceptaSesentaYCuatroCaracteres() {
        assertDoesNotThrow(() -> politica.validar("a".repeat(64), CORREO));
    }

    @Test
    @DisplayName("rechaza una contraseña de 65 caracteres")
    void rechazaSesentaYCincoCaracteres() {
        ErrorDeRegistro error = validarEsperandoError("a".repeat(65));

        assertEquals(Motivo.CONTRASENA_MUY_LARGA, error.motivo());
    }

    @Test
    @DisplayName("cuenta cada emoji como un solo carácter: 11 emojis es demasiado corta")
    void cuentaCaracteresRealesYNoUnidadesInternas() {
        // Para Java, 11 emojis miden 22 con String.length(). Si la clase contara así,
        // esta contraseña pasaría el mínimo de 12 sin tenerlo.
        ErrorDeRegistro error = validarEsperandoError(EMOJI.repeat(11));

        assertEquals(Motivo.CONTRASENA_MUY_CORTA, error.motivo());
    }

    // ---------- Peso en bytes ----------

    @Test
    @DisplayName("acepta 18 emojis: son exactamente 72 bytes")
    void aceptaSetentaYDosBytes() {
        String contrasena = EMOJI.repeat(18);
        assertEquals(72, contrasena.getBytes(StandardCharsets.UTF_8).length);

        assertDoesNotThrow(() -> politica.validar(contrasena, CORREO));
    }

    @Test
    @DisplayName("rechaza 19 emojis: son 76 bytes aunque tenga menos de 64 caracteres")
    void rechazaMasDeSetentaYDosBytes() {
        ErrorDeRegistro error = validarEsperandoError(EMOJI.repeat(19));

        assertEquals(Motivo.CONTRASENA_SUPERA_BYTES, error.motivo());
    }

    // ---------- Sin transformar ----------

    @Test
    @DisplayName("no recorta espacios: los de los extremos cuentan para el largo")
    void losEspaciosCuentanParaElLargo() {
        // 10 letras más un espacio a cada lado: 12 caracteres. Si se recortara, quedarían 10.
        assertDoesNotThrow(() -> politica.validar(" abcdefghij ", CORREO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("rechaza una contraseña nula o vacía")
    void rechazaNulaOVacia(String contrasena) {
        ErrorDeRegistro error = validarEsperandoError(contrasena);

        assertEquals(Motivo.CONTRASENA_OBLIGATORIA, error.motivo());
    }

    // ---------- Igual al correo ----------

    @Test
    @DisplayName("rechaza una contraseña igual al correo")
    void rechazaIgualAlCorreo() {
        ErrorDeRegistro error = validarEsperandoError("ana@correo.com");

        assertEquals(Motivo.CONTRASENA_IGUAL_AL_CORREO, error.motivo());
    }

    @Test
    @DisplayName("rechaza una contraseña igual al correo aunque cambien las mayúsculas")
    void rechazaIgualAlCorreoConOtrasMayusculas() {
        ErrorDeRegistro error = validarEsperandoError("Ana@Correo.COM");

        assertEquals(Motivo.CONTRASENA_IGUAL_AL_CORREO, error.motivo());
    }

    @Test
    @DisplayName("la comparación con el correo no depende del idioma de la máquina")
    void laComparacionConElCorreoNoDependeDelIdioma() {
        // En turco, la "I" mayúscula pasa a "ı" sin punto. Si la clase usara el idioma
        // del sistema, "TITO@..." dejaría de parecerse a "tito@..." y la contraseña
        // igual al correo se aceptaría por error.
        CorreoNormalizado correo = new CorreoNormalizado("tito@correo.com");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                    () -> politica.validar("TITO@CORREO.COM", correo));

            assertEquals(Motivo.CONTRASENA_IGUAL_AL_CORREO, error.motivo());
        } finally {
            Locale.setDefault(original);
        }
    }

    // ---------- Contraseñas comunes ----------

    @Test
    @DisplayName("rechaza una contraseña que está en la lista de comunes")
    void rechazaContrasenaComun() {
        ErrorDeRegistro error = validarEsperandoError("password1234");

        assertEquals(Motivo.CONTRASENA_COMUN, error.motivo());
    }

    @Test
    @DisplayName("rechaza una contraseña común aunque cambien las mayúsculas")
    void rechazaContrasenaComunConOtrasMayusculas() {
        ErrorDeRegistro error = validarEsperandoError("PassWord1234");

        assertEquals(Motivo.CONTRASENA_COMUN, error.motivo());
    }

    // ---------- Orden y contenido del error ----------

    @Test
    @DisplayName("si falla por varias razones, informa primero el largo")
    void informaPrimeroElLargo() {
        // 65 caracteres con tilde: demasiados caracteres y además 130 bytes.
        ErrorDeRegistro error = validarEsperandoError("á".repeat(65));

        assertEquals(Motivo.CONTRASENA_MUY_LARGA, error.motivo());
    }

    @Test
    @DisplayName("el error indica que el campo inválido es la contraseña")
    void elErrorIndicaElCampo() {
        ErrorDeRegistro error = validarEsperandoError("corta");

        assertEquals("contrasena", error.campo());
    }

    @Test
    @DisplayName("el mensaje del error no repite la contraseña ni el correo")
    void elMensajeNoRevelaDatos() {
        ErrorDeRegistro porComun = validarEsperandoError("password1234");
        ErrorDeRegistro porCorreo = validarEsperandoError("ana@correo.com");

        assertFalse(porComun.getMessage().contains("password1234"));
        assertFalse(porCorreo.getMessage().contains("ana@correo.com"));
    }

    @Test
    @DisplayName("exige el correo: sin él no se puede aplicar la regla completa")
    void exigeElCorreo() {
        // Un correo nulo no es un error de la persona, sino de quien programa la llamada.
        assertThrows(NullPointerException.class,
                () -> politica.validar("una-clave-valida", null));
    }

    /** Valida la contraseña contra el correo de la prueba y devuelve el error esperado. */
    private ErrorDeRegistro validarEsperandoError(String contrasena) {
        return assertThrows(ErrorDeRegistro.class, () -> politica.validar(contrasena, CORREO));
    }
}
