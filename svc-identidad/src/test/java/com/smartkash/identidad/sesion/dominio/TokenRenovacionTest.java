package com.smartkash.identidad.sesion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link TokenRenovacion}.
 *
 * <p>Qué comprueban: que el token de renovación de una sesión nueva dura 7 días, abre su
 * propia familia y solo admite la huella del valor, nunca el valor que viaja en la cookie.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class TokenRenovacionTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T10:00:00Z");
    private static final UUID USUARIO = UUID.randomUUID();

    /** Una huella con la forma correcta: 64 cifras hexadecimales. */
    private static final String HUELLA = "ab".repeat(32);

    /** Un valor con la forma de los que viajan en la cookie: 43 caracteres. */
    private static final String VALOR_EN_CLARO = "A".repeat(43);

    @Test
    @DisplayName("el token de una sesión nueva caduca a los 7 días")
    void caducaALosSieteDias() {
        TokenRenovacion token = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA);

        assertEquals(AHORA, token.emitidoEn());
        assertEquals(AHORA.plus(Duration.ofDays(7)), token.expiraEn());
    }

    @Test
    @DisplayName("conserva a quién pertenece y la huella de su valor")
    void conservaDuenoYHuella() {
        TokenRenovacion token = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA);

        assertEquals(USUARIO, token.usuarioId());
        assertEquals(HUELLA, token.huella());
    }

    @Test
    @DisplayName("cada sesión nueva tiene su propio identificador y su propia familia")
    void cadaSesionAbreSuFamilia() {
        TokenRenovacion una = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA);
        TokenRenovacion otra = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA);

        assertNotNull(una.id());
        assertNotNull(una.familiaId());
        assertNotEquals(una.id(), otra.id());
        assertNotEquals(una.familiaId(), otra.familiaId());
    }

    @Test
    @DisplayName("está vigente hasta un instante antes de caducar, y ya no en el instante exacto")
    void sabeSiEstaVigente() {
        TokenRenovacion token = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA);
        Instant caducidad = AHORA.plus(Duration.ofDays(7));

        assertTrue(token.vigente(AHORA));
        assertTrue(token.vigente(caducidad.minusSeconds(1)));
        assertFalse(token.vigente(caducidad));
    }

    @Test
    @DisplayName("rechaza cualquier cosa que no tenga forma de huella, como el valor en claro")
    void soloAdmiteHuellas() {
        assertThrows(IllegalArgumentException.class,
                () -> TokenRenovacion.deSesionNueva(USUARIO, VALOR_EN_CLARO, AHORA));
        assertThrows(IllegalArgumentException.class,
                () -> TokenRenovacion.deSesionNueva(USUARIO, HUELLA.toUpperCase(), AHORA));
        assertThrows(IllegalArgumentException.class,
                () -> TokenRenovacion.deSesionNueva(USUARIO, "", AHORA));
    }

    @Test
    @DisplayName("exige todos sus datos y que caduque después de emitirse")
    void exigeDatosCoherentes() {
        UUID id = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> TokenRenovacion.deSesionNueva(null, HUELLA, AHORA));
        assertThrows(NullPointerException.class,
                () -> TokenRenovacion.deSesionNueva(USUARIO, null, AHORA));
        assertThrows(NullPointerException.class,
                () -> TokenRenovacion.deSesionNueva(USUARIO, HUELLA, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenRenovacion(id, id, USUARIO, HUELLA, AHORA, AHORA));
    }

    @Test
    @DisplayName("el texto de depuración no muestra la huella")
    void elTextoDeDepuracionNoRevelaLaHuella() {
        String texto = TokenRenovacion.deSesionNueva(USUARIO, HUELLA, AHORA).toString();

        assertFalse(texto.contains(HUELLA));
    }
}