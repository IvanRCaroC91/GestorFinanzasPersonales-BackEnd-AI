package com.smartkash.identidad.sesion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link ControlDeIntentos}.
 *
 * <p>Qué comprueban: cuándo los fallos de inicio de sesión provocan un bloqueo, cuánto dura,
 * cómo crece si los fallos continúan y cuándo el contador vuelve a empezar.</p>
 *
 * <p>Cómo simulan el tiempo: el contador no mira ningún reloj; recibe la hora como dato. Las
 * pruebas parten de un instante fijo y le suman minutos, así comprueban qué pasa "15 minutos
 * después" sin esperar de verdad.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class ControlDeIntentosTest {

    private static final Instant INICIO = Instant.parse("2026-11-16T10:00:00Z");
    private static final LimiteDeIntentos CUENTA = LimiteDeIntentos.POR_CUENTA;

    // ---------- Antes del límite ----------

    @Test
    @DisplayName("el primer fallo abre la ventana y no bloquea")
    void elPrimerFalloNoBloquea() {
        ControlDeIntentos control = ControlDeIntentos.trasPrimerFallo(INICIO, CUENTA);

        assertEquals(1, control.fallos());
        assertEquals(INICIO, control.ventanaInicio());
        assertNull(control.bloqueadoHasta());
        assertEquals(Optional.empty(), control.esperaRestante(INICIO));
    }

    @Test
    @DisplayName("cuatro fallos seguidos no bloquean la cuenta")
    void cuatroFallosNoBloquean() {
        ControlDeIntentos control = fallosSeguidos(4, INICIO, CUENTA);

        assertEquals(4, control.fallos());
        assertEquals(Optional.empty(), control.esperaRestante(INICIO));
    }

    // ---------- Al alcanzar el límite ----------

    @Test
    @DisplayName("el quinto fallo bloquea la cuenta durante 1 minuto")
    void elQuintoFalloBloqueaUnMinuto() {
        ControlDeIntentos control = fallosSeguidos(5, INICIO, CUENTA);

        assertEquals(5, control.fallos());
        assertEquals(INICIO.plus(minutos(1)), control.bloqueadoHasta());
        assertEquals(Optional.of(minutos(1)), control.esperaRestante(INICIO));
    }

    @Test
    @DisplayName("durante el bloqueo, la espera que falta va bajando")
    void laEsperaRestanteDisminuye() {
        ControlDeIntentos control = fallosSeguidos(5, INICIO, CUENTA);

        assertEquals(Optional.of(Duration.ofSeconds(20)),
                control.esperaRestante(INICIO.plusSeconds(40)));
    }

    @Test
    @DisplayName("en el instante exacto en que termina el bloqueo ya no hay que esperar")
    void alTerminarElBloqueoNoHayEspera() {
        ControlDeIntentos control = fallosSeguidos(5, INICIO, CUENTA);

        assertEquals(Optional.empty(), control.esperaRestante(INICIO.plus(minutos(1))));
    }

    @Test
    @DisplayName("un intento durante el bloqueo no suma fallos ni alarga la espera")
    void unIntentoDuranteElBloqueoNoCuenta() {
        ControlDeIntentos bloqueado = fallosSeguidos(5, INICIO, CUENTA);
        Instant enPlenoBloqueo = INICIO.plusSeconds(30);

        ControlDeIntentos despues = bloqueado.trasOtroFallo(enPlenoBloqueo, CUENTA);

        assertEquals(5, despues.fallos());
        assertEquals(INICIO.plus(minutos(1)), despues.bloqueadoHasta());
        assertEquals(bloqueado, despues);
    }

    @Test
    @DisplayName("por mucho que se insista durante el bloqueo, el contador no cambia")
    void insistirDuranteElBloqueoNoCambiaNada() {
        ControlDeIntentos bloqueado = fallosSeguidos(5, INICIO, CUENTA);

        ControlDeIntentos despues = bloqueado;
        for (int segundo = 0; segundo < 60; segundo++) {
            despues = despues.trasOtroFallo(INICIO.plusSeconds(segundo), CUENTA);
        }

        assertEquals(bloqueado, despues);
        // Al terminar el bloqueo, el siguiente fallo es el sexto, no el sexagésimo quinto.
        assertEquals(6, despues.trasOtroFallo(INICIO.plus(minutos(1)), CUENTA).fallos());
    }

    // ---------- Espera creciente ----------

    @Test
    @DisplayName("cada fallo tras un bloqueo duplica la espera: 2, 4 y 8 minutos, y luego 15")
    void cadaFalloAdicionalDuplicaLaEspera() {
        ControlDeIntentos control = fallosSeguidos(5, INICIO, CUENTA);

        control = falloAlTerminarElBloqueo(control);
        assertEquals(6, control.fallos());
        assertEquals(Optional.of(minutos(2)), esperaReciente(control));

        control = falloAlTerminarElBloqueo(control);
        assertEquals(Optional.of(minutos(4)), esperaReciente(control));

        control = falloAlTerminarElBloqueo(control);
        assertEquals(Optional.of(minutos(8)), esperaReciente(control));

        control = falloAlTerminarElBloqueo(control);
        assertEquals(Optional.of(minutos(15)), esperaReciente(control));
    }

    @Test
    @DisplayName("la espera nunca pasa de 15 minutos por muchos fallos que haya")
    void laEsperaTieneTope() {
        ControlDeIntentos control = fallosSeguidos(5, INICIO, CUENTA);

        for (int i = 0; i < 30; i++) {
            control = falloAlTerminarElBloqueo(control);
        }

        assertEquals(Optional.of(minutos(15)), esperaReciente(control));
    }

    @Test
    @DisplayName("el contador de fallos deja de crecer al llegar a su máximo")
    void elContadorTieneMaximo() {
        ControlDeIntentos control =
                new ControlDeIntentos(ControlDeIntentos.MAXIMO_FALLOS, INICIO, INICIO.plus(minutos(15)));

        ControlDeIntentos despues = control.trasOtroFallo(INICIO.plus(minutos(15)), CUENTA);

        assertEquals(ControlDeIntentos.MAXIMO_FALLOS, despues.fallos());
    }

    // ---------- Reinicio del contador ----------

    @Test
    @DisplayName("un fallo dentro de la ventana de 15 minutos se suma a los anteriores")
    void unFalloDentroDeLaVentanaSeSuma() {
        ControlDeIntentos control = fallosSeguidos(4, INICIO, CUENTA);
        Instant casiQuinceMinutos = INICIO.plus(minutos(15)).minusSeconds(1);

        ControlDeIntentos despues = control.trasOtroFallo(casiQuinceMinutos, CUENTA);

        assertEquals(5, despues.fallos());
        assertEquals(INICIO, despues.ventanaInicio());
        assertEquals(Optional.of(minutos(1)), despues.esperaRestante(casiQuinceMinutos));
    }

    @Test
    @DisplayName("un fallo cuando la ventana ya pasó empieza la cuenta de nuevo")
    void unFalloFueraDeLaVentanaReiniciaLaCuenta() {
        ControlDeIntentos control = fallosSeguidos(4, INICIO, CUENTA);
        Instant quinceMinutosDespues = INICIO.plus(minutos(15));

        ControlDeIntentos despues = control.trasOtroFallo(quinceMinutosDespues, CUENTA);

        assertEquals(1, despues.fallos());
        assertEquals(quinceMinutosDespues, despues.ventanaInicio());
        assertEquals(Optional.empty(), despues.esperaRestante(quinceMinutosDespues));
    }

    @Test
    @DisplayName("tras un bloqueo, la cuenta se reinicia solo después de 15 minutos sin fallos")
    void trasUnBloqueoHacenFaltaQuinceMinutosTranquilos() {
        ControlDeIntentos bloqueado = fallosSeguidos(5, INICIO, CUENTA);
        Instant finDelBloqueo = bloqueado.bloqueadoHasta();

        ControlDeIntentos pronto =
                bloqueado.trasOtroFallo(finDelBloqueo.plus(minutos(15)).minusSeconds(1), CUENTA);
        ControlDeIntentos tarde = bloqueado.trasOtroFallo(finDelBloqueo.plus(minutos(15)), CUENTA);

        // Antes de los 15 minutos tranquilos, el fallo cuenta como el sexto y la espera crece.
        assertEquals(6, pronto.fallos());
        // Pasados 15 minutos tranquilos, es como si fuera el primer fallo.
        assertEquals(1, tarde.fallos());
        assertNull(tarde.bloqueadoHasta());
    }

    @Test
    @DisplayName("sabe decir si ya caducó, para poder borrarlo")
    void sabeSiCaduco() {
        ControlDeIntentos sinBloqueo = fallosSeguidos(2, INICIO, CUENTA);
        ControlDeIntentos bloqueado = fallosSeguidos(5, INICIO, CUENTA);

        assertEquals(false, sinBloqueo.caducado(INICIO.plus(minutos(15)).minusSeconds(1), CUENTA));
        assertTrue(sinBloqueo.caducado(INICIO.plus(minutos(15)), CUENTA));
        assertEquals(false, bloqueado.caducado(INICIO.plus(minutos(15)), CUENTA));
        assertTrue(bloqueado.caducado(INICIO.plus(minutos(16)), CUENTA));
    }

    // ---------- Límite por dirección IP ----------

    @Test
    @DisplayName("por dirección IP se toleran 19 fallos y el vigésimo bloquea 1 minuto")
    void elLimitePorIpEsDeVeinteFallos() {
        ControlDeIntentos diecinueve = fallosSeguidos(19, INICIO, LimiteDeIntentos.POR_IP);
        ControlDeIntentos veinte = diecinueve.trasOtroFallo(INICIO, LimiteDeIntentos.POR_IP);

        assertEquals(Optional.empty(), diecinueve.esperaRestante(INICIO));
        assertEquals(Optional.of(minutos(1)), veinte.esperaRestante(INICIO));
    }

    @Test
    @DisplayName("los límites son 5 fallos por cuenta y 20 por IP, en ventanas de 15 minutos")
    void losLimitesSonLosAcordados() {
        assertEquals(5, LimiteDeIntentos.POR_CUENTA.fallosPermitidos());
        assertEquals(minutos(15), LimiteDeIntentos.POR_CUENTA.ventana());
        assertEquals(20, LimiteDeIntentos.POR_IP.fallosPermitidos());
        assertEquals(minutos(15), LimiteDeIntentos.POR_IP.ventana());
    }

    // ---------- Datos coherentes ----------

    @Test
    @DisplayName("no admite un contador sin fallos ni sin inicio de ventana")
    void exigeDatosCoherentes() {
        assertThrows(IllegalArgumentException.class, () -> new ControlDeIntentos(0, INICIO, null));
        assertThrows(NullPointerException.class, () -> new ControlDeIntentos(1, null, null));
    }

    @Test
    @DisplayName("un límite debe tolerar al menos un fallo y tener una ventana positiva")
    void elLimiteExigeDatosCoherentes() {
        assertThrows(IllegalArgumentException.class, () -> new LimiteDeIntentos(0, minutos(15)));
        assertThrows(IllegalArgumentException.class, () -> new LimiteDeIntentos(5, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new LimiteDeIntentos(5, null));
    }

    // ---------- Ayudas ----------

    /* Simula varios fallos en el mismo instante: el primero abre la ventana y el resto se suman. */
    private static ControlDeIntentos fallosSeguidos(int cuantos, Instant cuando, LimiteDeIntentos limite) {
        ControlDeIntentos control = ControlDeIntentos.trasPrimerFallo(cuando, limite);
        for (int i = 1; i < cuantos; i++) {
            control = control.trasOtroFallo(cuando, limite);
        }
        return control;
    }

    /* Simula que la persona espera a que termine el bloqueo y vuelve a fallar justo entonces. */
    private static ControlDeIntentos falloAlTerminarElBloqueo(ControlDeIntentos control) {
        return control.trasOtroFallo(control.bloqueadoHasta(), CUENTA);
    }

    /* Espera que impuso el último fallo, medida desde el momento de ese fallo. */
    private static Optional<Duration> esperaReciente(ControlDeIntentos control) {
        Duration espera = EsperaCreciente.tras(control.fallos() - CUENTA.fallosPermitidos());
        return control.esperaRestante(control.bloqueadoHasta().minus(espera));
    }

    private static Duration minutos(long cuantos) {
        return Duration.ofMinutes(cuantos);
    }
}