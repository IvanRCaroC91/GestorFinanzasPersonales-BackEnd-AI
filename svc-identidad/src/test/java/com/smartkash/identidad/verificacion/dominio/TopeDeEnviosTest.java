package com.smartkash.identidad.verificacion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
    @DisplayName("por debajo del tope no hay que esperar")
    void porDebajoDelTopeNoHayQueEsperar() {
        List<Instant> emitidos = List.of(AHORA.minus(Duration.ofMinutes(30)), AHORA.minus(Duration.ofMinutes(10)));

        assertThat(TopeDeEnvios.esperaHastaOtro(emitidos, AHORA)).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("con el tope alcanzado hay que esperar a que el más antiguo salga de la ventana")
    void conElTopeHayQueEsperarAlMasAntiguo() {
        List<Instant> emitidos = List.of(AHORA.minus(Duration.ofMinutes(50)),
                AHORA.minus(Duration.ofMinutes(30)), AHORA.minus(Duration.ofMinutes(10)));

        assertThat(TopeDeEnvios.esperaHastaOtro(emitidos, AHORA)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("el orden en que llegan las fechas no cambia la espera")
    void elOrdenNoImporta() {
        List<Instant> emitidos = List.of(AHORA.minus(Duration.ofMinutes(10)),
                AHORA.minus(Duration.ofMinutes(50)), AHORA.minus(Duration.ofMinutes(30)));

        assertThat(TopeDeEnvios.esperaHastaOtro(emitidos, AHORA)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("si hubiera más del tope, la espera llega hasta que solo queden dos en la ventana")
    void conMasDelTopeSeEsperaHastaQueQuedenDos() {
        List<Instant> emitidos = List.of(AHORA.minus(Duration.ofMinutes(55)), AHORA.minus(Duration.ofMinutes(40)),
                AHORA.minus(Duration.ofMinutes(30)), AHORA.minus(Duration.ofMinutes(10)));

        assertThat(TopeDeEnvios.esperaHastaOtro(emitidos, AHORA)).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    @DisplayName("la ventana empieza una hora antes del momento de la consulta")
    void laVentanaEmpiezaUnaHoraAntes() {
        assertThat(TopeDeEnvios.inicioDeLaVentana(AHORA)).isEqualTo(AHORA.minus(Duration.ofHours(1)));
    }
}