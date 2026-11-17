package com.smartkash.identidad.sesion.adaptadores.web;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba cómo se convierte el tiempo de espera de un bloqueo en la cabecera
 * {@code Retry-After}, que se expresa en segundos enteros.
 *
 * <p>Regla: se redondea siempre hacia arriba y nunca es menos de 1. Si se redondeara hacia
 * abajo, quien obedece la cabecera volvería un instante antes de que termine el bloqueo y
 * recibiría otro 429.</p>
 */
class ManejadorErroresDeInicioSesionTest {

    @Test
    void un_minuto_exacto_son_60_segundos() {
        assertThat(ManejadorErroresDeInicioSesion.segundosDeEspera(Duration.ofMinutes(1))).isEqualTo(60);
    }

    @Test
    void una_fraccion_de_segundo_cuenta_como_un_segundo_entero() {
        assertThat(ManejadorErroresDeInicioSesion.segundosDeEspera(Duration.ofMillis(59_001))).isEqualTo(60);
        assertThat(ManejadorErroresDeInicioSesion.segundosDeEspera(Duration.ofNanos(1))).isEqualTo(1);
    }

    @Test
    void nunca_es_menos_de_un_segundo() {
        assertThat(ManejadorErroresDeInicioSesion.segundosDeEspera(Duration.ZERO)).isEqualTo(1);
    }
}