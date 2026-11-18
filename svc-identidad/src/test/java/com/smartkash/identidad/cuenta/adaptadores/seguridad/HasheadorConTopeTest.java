package com.smartkash.identidad.cuenta.adaptadores.seguridad;

import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.HasheadorSaturado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el tope de cálculos de hash simultáneos, sin Spring y sin Argon2.
 *
 * <p>Qué hace: el hasheador real se sustituye por uno falso que se queda «calculando» hasta que
 * la prueba le abre una barrera ({@link CountDownLatch}: un contador que bloquea a quien lo
 * espera hasta que llega a cero). Así la prueba decide cuándo termina cada cálculo y puede
 * ocupar todos los turnos a propósito.</p>
 *
 * <p>Cada prueba tiene un tiempo máximo ({@code @Timeout}) para que un fallo no deje la
 * ejecución colgada para siempre.</p>
 */
@Timeout(10)
class HasheadorConTopeTest {

    private static final Duration ESPERA = Duration.ofMillis(300);

    private final HasheadorLento real = new HasheadorLento();
    private final ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void cerrar() {
        real.barrera.countDown();
        hilos.shutdownNow();
    }

    // ---------- Sin saturación ----------

    /** Con turnos libres, cada operación llega al hasheador real con los mismos datos. */
    @Test
    void con_turnos_libres_delega_en_el_hasheador_real() {
        real.barrera.countDown();
        HasheadorConTope tope = new HasheadorConTope(real, 2, ESPERA);

        assertThat(tope.hashear("clave")).isEqualTo("hash-de-clave");
        assertThat(tope.coincide("clave", "hash-de-clave")).isTrue();
        assertThat(tope.coincide("otra", "hash-de-clave")).isFalse();
    }

    /**
     * Con muchas peticiones a la vez, nunca hay más cálculos en marcha que turnos, y todas
     * terminan porque la espera da tiempo a que se liberen.
     */
    @Test
    void nunca_calcula_mas_de_lo_permitido_a_la_vez() throws Exception {
        HasheadorConTope tope = new HasheadorConTope(real, 3, Duration.ofSeconds(5));

        List<Future<String>> resultados = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            resultados.add(hilos.submit(() -> tope.hashear("clave")));
        }
        real.esperarEnMarcha(3);
        real.barrera.countDown();
        for (Future<String> resultado : resultados) {
            assertThat(resultado.get()).isEqualTo("hash-de-clave");
        }

        assertThat(real.maximoALaVez.get()).isEqualTo(3);
    }

    // ---------- Con saturación ----------

    /** Si todos los turnos siguen ocupados al acabar la espera, se rechaza sin calcular. */
    @Test
    void si_no_hay_turno_a_tiempo_lanza_saturado_sin_calcular() throws Exception {
        HasheadorConTope tope = new HasheadorConTope(real, 1, ESPERA);
        hilos.submit(() -> tope.hashear("ocupa"));
        real.esperarEnMarcha(1);

        assertThatThrownBy(() -> tope.hashear("clave")).isInstanceOf(HasheadorSaturado.class);
        assertThatThrownBy(() -> tope.coincide("clave", "hash")).isInstanceOf(HasheadorSaturado.class);
        assertThat(real.llamadas.get()).as("solo el que ocupaba el turno").isEqualTo(1);
    }

    /** La espera está acotada: el rechazo llega al acabar la espera, no mucho después. */
    @Test
    void la_espera_esta_acotada() throws Exception {
        HasheadorConTope tope = new HasheadorConTope(real, 1, ESPERA);
        hilos.submit(() -> tope.hashear("ocupa"));
        real.esperarEnMarcha(1);

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> tope.hashear("clave")).isInstanceOf(HasheadorSaturado.class);
        Duration esperado = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(esperado).isBetween(ESPERA.minusMillis(50), ESPERA.plusSeconds(2));
    }

    /** Si el turno se libera durante la espera, el cálculo se hace. */
    @Test
    void si_el_turno_se_libera_a_tiempo_calcula() throws Exception {
        HasheadorConTope tope = new HasheadorConTope(real, 1, Duration.ofSeconds(5));
        hilos.submit(() -> tope.hashear("ocupa"));
        real.esperarEnMarcha(1);

        Future<String> segundo = hilos.submit(() -> tope.hashear("clave"));
        real.barrera.countDown();

        assertThat(segundo.get()).isEqualTo("hash-de-clave");
    }

    /** El turno se devuelve aunque el cálculo falle: si no, cada fallo perdería un turno. */
    @Test
    void el_turno_se_devuelve_aunque_el_calculo_falle() {
        real.barrera.countDown();
        HasheadorConTope tope = new HasheadorConTope(real, 1, ESPERA);

        assertThatThrownBy(() -> tope.hashear(HasheadorLento.FALLA)).isInstanceOf(IllegalStateException.class);

        assertThat(tope.hashear("clave")).isEqualTo("hash-de-clave");
    }

    /**
     * Si el hilo se interrumpe mientras espera turno, se rechaza como saturado y la marca de
     * interrupción se conserva, para que quien gestiona el hilo sepa que se le pidió parar.
     */
    @Test
    void si_se_interrumpe_la_espera_lanza_saturado_y_conserva_la_interrupcion() throws Exception {
        HasheadorConTope tope = new HasheadorConTope(real, 1, Duration.ofSeconds(5));
        hilos.submit(() -> tope.hashear("ocupa"));
        real.esperarEnMarcha(1);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> tope.hashear("clave")).isInstanceOf(HasheadorSaturado.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    /** No se puede crear con cero turnos ni con una espera negativa. */
    @Test
    void rechaza_una_configuracion_sin_sentido() {
        assertThatThrownBy(() -> new HasheadorConTope(real, 0, ESPERA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HasheadorConTope(real, 1, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Ayudas ----------

    /* Hasheador falso: «calcula» hasta que se abre la barrera y anota cuántos van a la vez. */
    private static final class HasheadorLento implements HasheadorContrasena {

        static final String FALLA = "falla";

        final CountDownLatch barrera = new CountDownLatch(1);
        final AtomicInteger enMarcha = new AtomicInteger();
        final AtomicInteger maximoALaVez = new AtomicInteger();
        final AtomicInteger llamadas = new AtomicInteger();

        @Override
        public String hashear(String contrasena) {
            calcular(contrasena);
            return "hash-de-" + contrasena;
        }

        @Override
        public boolean coincide(String contrasena, String hash) {
            calcular(contrasena);
            return hash.equals("hash-de-" + contrasena);
        }

        private void calcular(String contrasena) {
            llamadas.incrementAndGet();
            maximoALaVez.accumulateAndGet(enMarcha.incrementAndGet(), Math::max);
            try {
                if (FALLA.equals(contrasena)) {
                    throw new IllegalStateException("fallo simulado");
                }
                barrera.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                enMarcha.decrementAndGet();
            }
        }

        /* Espera a que haya al menos esa cantidad de cálculos en marcha. */
        void esperarEnMarcha(int cuantos) throws InterruptedException {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (enMarcha.get() < cuantos && System.nanoTime() < limite) {
                Thread.sleep(10);
            }
            assertThat(enMarcha.get()).as("cálculos en marcha").isGreaterThanOrEqualTo(cuantos);
        }
    }
}
