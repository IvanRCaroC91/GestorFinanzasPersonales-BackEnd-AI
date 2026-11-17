package com.smartkash.identidad.verificacion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del tope de enlaces de verificación: como mucho 3 en la última hora por persona.
 */
class TopeDeEnviosTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");

    @ParameterizedTest(name = "con {0} enlaces en la última hora se admite otro")
    @ValueSource(ints = {0, 1, 2})
    void pordebajoDelTopeSeAdmiteOtro(int emitidos) {
        assertThat(TopeDeEnvios.admiteOtro(emitidos)).isTrue();
    }

    @ParameterizedTest(name = "con {0} enlaces en la última hora no se admite otro")
    @ValueSource(ints = {3, 4, 100})
    void alcanzadoElTopeNoSeAdmiteOtro(int emitidos) {
        assertThat(TopeDeEnvios.admiteOtro(emitidos)).isFalse();
    }

    @Test
    @DisplayName("la ventana empieza una hora antes del momento de la consulta")
    void laVentanaEmpiezaUnaHoraAntes() {
        assertThat(TopeDeEnvios.inicioDeLaVentana(AHORA)).isEqualTo(AHORA.minus(Duration.ofHours(1)));
    }
}