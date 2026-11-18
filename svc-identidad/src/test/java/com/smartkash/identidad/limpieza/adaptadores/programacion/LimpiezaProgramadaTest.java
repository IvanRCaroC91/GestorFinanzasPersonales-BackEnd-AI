package com.smartkash.identidad.limpieza.adaptadores.programacion;

import com.smartkash.identidad.PruebaConContenedores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba la limpieza con el servicio completo: que está programada y que, al ejecutarse,
 * borra de verdad.
 *
 * <p>Qué es {@link ScheduledTaskHolder}: la parte de Spring que guarda la lista de tareas
 * programadas. Preguntarle a ella comprueba la programación real del servicio, no solo que el
 * método lleve una anotación.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class LimpiezaProgramadaTest extends PruebaConContenedores {

    private static final SecureRandom AZAR = new SecureRandom();

    @Autowired
    private ScheduledTaskHolder programador;

    @Autowired
    private LimpiezaProgramada limpieza;

    @Autowired
    private JdbcClient jdbc;

    /**
     * Hay exactamente una tarea de limpieza, y se ejecuta cada día a las 03:17 en hora UTC.
     *
     * <p>El reloj que se le pasa está en la zona de Bogotá (cinco horas menos que UTC) a
     * propósito. Una regla {@code cron} sin zona propia usa la del reloj, y entonces la
     * siguiente ejecución sería a las 03:17 de Bogotá, es decir, a las 08:17 UTC. Solo si la
     * tarea fija su zona en UTC sale 03:17 UTC, que es lo que se comprueba.</p>
     */
    @Test
    void esta_programada_cada_dia_a_las_3_17_utc() {
        List<ScheduledTask> tareas = programador.getScheduledTasks().stream()
                .filter(tarea -> tarea.toString().contains(LimpiezaProgramada.class.getName()))
                .toList();

        assertThat(tareas).hasSize(1);
        CronTask tarea = (CronTask) tareas.getFirst().getTask();
        assertThat(tarea.getExpression()).isEqualTo("0 17 3 * * *");
        Clock reloj = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneId.of("America/Bogota"));
        assertThat(((CronTrigger) tarea.getTrigger()).nextExecution(new SimpleTriggerContext(reloj)))
                .isEqualTo(Instant.parse("2026-11-18T03:17:00Z"));
    }

    /**
     * Al ejecutarse con el reloj real, borra un contador sin actividad desde hace dos días y
     * conserva uno de hace un minuto.
     */
    @Test
    void al_ejecutarse_borra_lo_caducado_y_conserva_lo_reciente() {
        Instant ahora = Instant.now();
        String viejo = contador(ahora.minus(Duration.ofDays(2)));
        String reciente = contador(ahora.minus(Duration.ofMinutes(1)));

        limpieza.limpiar();

        assertThat(existeContador(viejo)).isFalse();
        assertThat(existeContador(reciente)).isTrue();
    }

    // ---------- Ayudas ----------

    private String contador(Instant ventanaInicio) {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        String clave = HexFormat.of().formatHex(bytes);
        jdbc.sql("""
                        INSERT INTO bloqueos_acceso (clave_hash, tipo, fallos, ventana_inicio)
                        VALUES (?, 'IP', 1, ?)
                        """)
                .params(clave, Timestamp.from(ventanaInicio))
                .update();
        return clave;
    }

    private boolean existeContador(String clave) {
        return jdbc.sql("SELECT count(*) FROM bloqueos_acceso WHERE clave_hash = ?")
                .param(clave).query(Integer.class).single() == 1;
    }
}
