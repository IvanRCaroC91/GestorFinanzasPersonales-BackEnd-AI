package com.smartkash.identidad.limpieza.aplicacion;

import com.smartkash.identidad.limpieza.puertos.AlmacenDeDatosCaducados;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba qué fechas límite calcula la limpieza y qué hace si un borrado falla.
 *
 * <p>Qué hace: el almacén es un sustituto que anota con qué fecha límite se le pidió borrar y
 * responde una cantidad fija. El reloj está parado en un instante conocido, así cada límite se
 * puede comparar con una fecha exacta.</p>
 */
class LimpiarDatosCaducadosTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T03:17:00Z");

    private final AlmacenFalso almacen = new AlmacenFalso();
    private final LimpiarDatosCaducados limpieza =
            new LimpiarDatosCaducados(almacen, Clock.fixed(AHORA, ZoneOffset.UTC));

    /**
     * Los contadores de intentos se conservan 24 horas desde el último fallo. Lo último que
     * guarda un contador es el inicio de su ventana o el fin de su bloqueo, y el último fallo
     * pudo llegar hasta 15 minutos después. Por eso el límite es 24 h y 15 min antes de ahora.
     */
    @Test
    void los_contadores_se_borran_24_horas_despues_del_ultimo_fallo_posible() {
        limpieza.ejecutar();

        assertThat(almacen.limiteContadores).isEqualTo(Instant.parse("2026-11-16T03:02:00Z"));
    }

    /** Los tokens de renovación se conservan 7 días después de caducar. */
    @Test
    void los_tokens_de_renovacion_se_borran_7_dias_despues_de_caducar() {
        limpieza.ejecutar();

        assertThat(almacen.limiteRenovacion).isEqualTo(Instant.parse("2026-11-10T03:17:00Z"));
    }

    /** Los enlaces de un solo uso se conservan 7 días después de caducar, usados o no. */
    @Test
    void los_enlaces_de_un_solo_uso_se_borran_7_dias_despues_de_caducar() {
        limpieza.ejecutar();

        assertThat(almacen.limiteUnSoloUso).isEqualTo(Instant.parse("2026-11-10T03:17:00Z"));
    }

    /** Devuelve cuántas filas se borraron de cada tipo, para dejarlo en el log. */
    @Test
    void devuelve_cuanto_se_borro_de_cada_tipo() {
        ResultadoDeLimpieza resultado = limpieza.ejecutar();

        assertThat(resultado).isEqualTo(new ResultadoDeLimpieza(3, 2, 1));
        assertThat(resultado.total()).isEqualTo(6);
    }

    /**
     * Si un borrado falla, los otros se hacen igualmente, y al final sale el error para que
     * quede registrado. Un fallo en una tabla no debe dejar las demás sin limpiar.
     */
    @Test
    void si_un_borrado_falla_los_demas_se_hacen_y_el_error_sale_al_final() {
        almacen.fallaEn = "contadores";

        assertThatThrownBy(limpieza::ejecutar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fallo simulado en contadores");

        assertThat(almacen.llamadas).containsExactly("contadores", "renovacion", "un-solo-uso");
    }

    /** Si fallan varios, sale el primero y los demás van adjuntos a él. */
    @Test
    void si_fallan_varios_sale_el_primero_con_los_demas_adjuntos() {
        almacen.fallaEn = "todos";

        assertThatThrownBy(limpieza::ejecutar)
                .hasMessage("fallo simulado en contadores")
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(2));
    }

    /* Sustituto del almacén: anota los límites y responde 3, 2 y 1 filas borradas. */
    private static final class AlmacenFalso implements AlmacenDeDatosCaducados {

        Instant limiteContadores;
        Instant limiteRenovacion;
        Instant limiteUnSoloUso;
        String fallaEn = "";
        final List<String> llamadas = new ArrayList<>();

        @Override
        public long borrarContadoresSinActividadDesde(Instant limite) {
            anotar("contadores");
            limiteContadores = limite;
            return 3;
        }

        @Override
        public long borrarTokensDeRenovacionCaducadosAntesDe(Instant limite) {
            anotar("renovacion");
            limiteRenovacion = limite;
            return 2;
        }

        @Override
        public long borrarEnlacesDeUnSoloUsoCaducadosAntesDe(Instant limite) {
            anotar("un-solo-uso");
            limiteUnSoloUso = limite;
            return 1;
        }

        private void anotar(String cual) {
            llamadas.add(cual);
            if (fallaEn.equals(cual) || fallaEn.equals("todos")) {
                throw new IllegalStateException("fallo simulado en " + cual);
            }
        }
    }
}
