package com.smartkash.identidad.limpieza.adaptadores.programacion;

import com.smartkash.identidad.limpieza.aplicacion.LimpiarDatosCaducados;
import com.smartkash.identidad.limpieza.aplicacion.ResultadoDeLimpieza;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lanza la limpieza de datos caducados una vez al día.
 *
 * <p>Qué es una tarea programada: un método que Spring ejecuta solo, sin que llegue ninguna
 * petición, según un calendario. {@code @Scheduled} lo marca y la expresión {@code cron} dice
 * cuándo, con seis campos: segundo, minuto, hora, día del mes, mes y día de la semana. Por
 * ejemplo, {@code 0 17 3 * * *} significa «a las 03:17:00, todos los días».</p>
 *
 * <p>Decisiones:</p>
 * <ul>
 *   <li><b>A las 03:17 UTC:</b> de madrugada hay poco uso. El minuto 17, y no el 0, evita
 *       coincidir con otras tareas que suelen programarse a las horas en punto. La zona UTC
 *       hace que la hora no dependa de la configuración de la máquina ni de los cambios de
 *       horario de verano.</li>
 *   <li><b>Se puede cambiar o apagar</b> con la propiedad {@code identidad.limpieza.cron}; el
 *       valor {@code -} la desactiva.</li>
 *   <li><b>Varias copias del servicio a la vez no son un problema:</b> si dos ejecutan la
 *       limpieza al mismo tiempo, la segunda no encuentra nada que borrar.</li>
 * </ul>
 *
 * <p>Si la limpieza falla, el error sale del método y Spring lo escribe en el log. La tarea
 * no se cancela: se vuelve a intentar al día siguiente.</p>
 */
@Component
public class LimpiezaProgramada {

    private static final Logger LOG = LoggerFactory.getLogger(LimpiezaProgramada.class);

    private final LimpiarDatosCaducados limpieza;

    /**
     * Crea la tarea.
     *
     * @param limpieza el caso de uso que decide y borra lo caducado
     */
    LimpiezaProgramada(LimpiarDatosCaducados limpieza) {
        this.limpieza = limpieza;
    }

    /**
     * Ejecuta la limpieza y deja en el log cuántas filas borró de cada tipo.
     */
    @Scheduled(cron = "${identidad.limpieza.cron:0 17 3 * * *}", zone = "UTC")
    public void limpiar() {
        ResultadoDeLimpieza resultado = limpieza.ejecutar();
        LOG.info("Limpieza de datos caducados: {} contadores, {} tokens de renovación y {} enlaces de un solo uso borrados",
                resultado.contadores(), resultado.tokensDeRenovacion(), resultado.enlacesDeUnSoloUso());
    }
}
