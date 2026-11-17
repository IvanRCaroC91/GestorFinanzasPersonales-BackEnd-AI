package com.smartkash.identidad.registro.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link Autorizacion}.
 *
 * <p>Qué comprueban: que la prueba de que una persona aceptó la política de tratamiento
 * de datos guarda quién la aceptó, qué versión, por qué canal y en qué momento, y que
 * ese momento es el que se le entrega desde fuera (no lo calcula ella).</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class AutorizacionTest {

    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant MOMENTO = Instant.parse("2026-01-15T10:30:00Z");

    @Test
    @DisplayName("la aceptación de la política guarda quién, qué versión, cuándo y por qué canal")
    void guardaLaPruebaCompleta() {
        Autorizacion autorizacion = Autorizacion.aceptacionDePolitica(USUARIO, "1.0", MOMENTO);

        assertNotNull(autorizacion.id());
        assertEquals(USUARIO, autorizacion.usuarioId());
        assertEquals("1.0", autorizacion.versionPolitica());
        assertTrue(autorizacion.otorgada());
        assertEquals("WEB", autorizacion.canal());
        assertEquals(MOMENTO, autorizacion.registradaEn());
    }

    @Test
    @DisplayName("exige el usuario y el momento")
    void exigeUsuarioYMomento() {
        assertThrows(NullPointerException.class,
                () -> Autorizacion.aceptacionDePolitica(null, "1.0", MOMENTO));
        assertThrows(NullPointerException.class,
                () -> Autorizacion.aceptacionDePolitica(USUARIO, "1.0", null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "12345678901"})
    @DisplayName("rechaza una versión de política nula, vacía o de más de 10 caracteres")
    void rechazaVersionInvalida(String version) {
        assertThrows(IllegalArgumentException.class,
                () -> Autorizacion.aceptacionDePolitica(USUARIO, version, MOMENTO));
    }
}
