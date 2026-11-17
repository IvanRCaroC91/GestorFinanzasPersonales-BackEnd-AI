package com.smartkash.identidad.cuenta.dominio;

import com.smartkash.identidad.registro.dominio.ErrorDeRegistro;
import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link Usuario}.
 *
 * <p>Qué comprueban: que una cuenta nueva nace siempre con el rol y el estado que decide
 * el servidor, que el nombre cumple su largo, que el correo original y el normalizado
 * corresponden a la misma dirección y que el texto de depuración del usuario no muestra
 * el hash de la contraseña ni datos personales.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class UsuarioTest {

    /** Un emoji: un solo carácter para la persona, dos unidades internas para Java. */
    private static final String EMOJI = "😀";

    private static final String CORREO_ESCRITO = "Ana@Correo.COM";
    private static final CorreoNormalizado CORREO = new CorreoNormalizado(CORREO_ESCRITO);

    /** Texto inventado que hace de hash; no es una contraseña ni un hash real. */
    private static final String HASH = "hash-de-prueba";

    // ---------- Cuenta nueva ----------

    @Test
    @DisplayName("una cuenta nueva nace con rol USUARIO y pendiente de verificación")
    void laCuentaNuevaTieneRolYEstadoFijos() {
        Usuario usuario = Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, HASH);

        assertEquals(Rol.USUARIO, usuario.rol());
        assertEquals(EstadoCuenta.PENDIENTE_VERIFICACION, usuario.estado());
    }

    @Test
    @DisplayName("cada cuenta nueva recibe un identificador propio")
    void cadaCuentaNuevaTieneSuIdentificador() {
        Usuario una = Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, HASH);
        Usuario otra = Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, HASH);

        assertNotNull(una.id());
        assertNotEquals(una.id(), otra.id());
    }

    @Test
    @DisplayName("conserva los datos con los que se creó")
    void conservaLosDatos() {
        Usuario usuario = Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, HASH);

        assertEquals("Ana", usuario.nombre());
        assertEquals(CORREO, usuario.correo());
        assertEquals(HASH, usuario.hashContrasena());
    }

    // ---------- Nombre ----------

    @Test
    @DisplayName("guarda el nombre sin espacios en los extremos")
    void recortaElNombre() {
        Usuario usuario = Usuario.nuevo("  Ana María  ", CORREO_ESCRITO, CORREO, HASH);

        assertEquals("Ana María", usuario.nombre());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("rechaza un nombre nulo, vacío o hecho solo de espacios")
    void rechazaNombreVacio(String nombre) {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> Usuario.nuevo(nombre, CORREO_ESCRITO, CORREO, HASH));

        assertEquals(Motivo.NOMBRE_OBLIGATORIO, error.motivo());
        assertEquals("nombre", error.campo());
    }

    @Test
    @DisplayName("acepta un nombre de exactamente 100 caracteres")
    void aceptaNombreDeCienCaracteres() {
        assertDoesNotThrow(() -> Usuario.nuevo("a".repeat(100), CORREO_ESCRITO, CORREO, HASH));
    }

    @Test
    @DisplayName("rechaza un nombre de 101 caracteres")
    void rechazaNombreDeCientoUnCaracteres() {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> Usuario.nuevo("a".repeat(101), CORREO_ESCRITO, CORREO, HASH));

        assertEquals(Motivo.NOMBRE_MUY_LARGO, error.motivo());
        assertEquals("nombre", error.campo());
    }

    @Test
    @DisplayName("cuenta cada emoji del nombre como un solo carácter")
    void cuentaCaracteresRealesEnElNombre() {
        // 100 emojis miden 200 con String.length(); contados como los ve la persona son 100.
        assertDoesNotThrow(() -> Usuario.nuevo(EMOJI.repeat(100), CORREO_ESCRITO, CORREO, HASH));
    }

    // ---------- Correo ----------

    @Test
    @DisplayName("guarda el correo original con sus mayúsculas y sin espacios en los extremos")
    void guardaElCorreoOriginal() {
        Usuario usuario = Usuario.nuevo("Ana", "  Ana@Correo.COM ", CORREO, HASH);

        assertEquals("Ana@Correo.COM", usuario.correoOriginal());
    }

    @Test
    @DisplayName("rechaza un correo original que no corresponde al correo normalizado")
    void rechazaCorreosQueNoCorresponden() {
        assertThrows(IllegalArgumentException.class,
                () -> Usuario.nuevo("Ana", "otra@correo.com", CORREO, HASH));
    }

    // ---------- Hash ----------

    @Test
    @DisplayName("exige el hash de la contraseña")
    void exigeElHash() {
        assertThrows(NullPointerException.class,
                () -> Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, null));
        assertThrows(IllegalArgumentException.class,
                () -> Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, "  "));
    }

    @Test
    @DisplayName("el texto de depuración no muestra el hash ni datos personales")
    void elTextoDeDepuracionNoRevelaDatos() {
        Usuario usuario = Usuario.nuevo("Ana", CORREO_ESCRITO, CORREO, HASH);

        String texto = usuario.toString();

        assertFalse(texto.contains(HASH));
        assertFalse(texto.toLowerCase().contains("ana"));
    }
}
