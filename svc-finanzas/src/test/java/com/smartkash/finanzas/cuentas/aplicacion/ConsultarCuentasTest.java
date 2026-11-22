package com.smartkash.finanzas.cuentas.aplicacion;

import com.smartkash.finanzas.cuentas.dominio.Cuenta;
import com.smartkash.finanzas.cuentas.dominio.TipoDeCuenta;
import com.smartkash.finanzas.cuentas.puertos.RepositorioCuentas;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pruebas del caso de uso {@link ConsultarCuentas}.
 *
 * <p>Qué es un repositorio falso: una implementación del puerto {@link RepositorioCuentas}
 * escrita a mano para la prueba. No tiene base de datos: devuelve la lista que se le da y anota
 * con qué usuario lo llamaron. Así la prueba controla lo que «hay guardado» y puede comprobar lo
 * que el caso de uso preguntó, sin arrancar Spring ni Docker.</p>
 *
 * <p>Por qué a mano y no con una librería de dobles: el puerto tiene un solo método, y una
 * clase de diez líneas se lee mejor que la configuración de un doble.</p>
 */
class ConsultarCuentasTest {

    private final RepositorioFalso repositorio = new RepositorioFalso();
    private final ConsultarCuentas consultar = new ConsultarCuentas(repositorio);

    /**
     * El caso de uso entrega las cuentas que devuelve el repositorio, en el mismo orden: el orden
     * por nombre ya lo decide la consulta y el caso de uso no debe alterarlo.
     */
    @Test
    void entrega_lo_que_devuelve_el_repositorio() {
        Cuenta ahorros = cuenta("Ahorros");
        Cuenta cartera = cuenta("Cartera");
        repositorio.respuesta = List.of(ahorros, cartera);

        List<Cuenta> resultado = consultar.consultarCuentasUsuario(UUID.randomUUID());

        assertThat(resultado).containsExactly(ahorros, cartera);
    }

    /**
     * Regla de seguridad: el repositorio se consulta una sola vez y únicamente con el usuario que
     * recibió el caso de uso, que en el servicio real sale del token de acceso.
     */
    @Test
    void pregunta_solo_por_el_usuario_recibido() {
        UUID usuario = UUID.randomUUID();

        consultar.consultarCuentasUsuario(usuario);

        assertThat(repositorio.usuariosConsultados).containsExactly(usuario);
    }

    /**
     * Sin usuario no hay consulta: se rechaza antes de llegar al repositorio, para que un fallo
     * de quien llama nunca acabe en una búsqueda sin dueño.
     */
    @Test
    void rechaza_un_usuario_nulo() {
        assertThatNullPointerException()
                .isThrownBy(() -> consultar.consultarCuentasUsuario(null))
                .withMessageContaining("usuario");

        assertThat(repositorio.usuariosConsultados).isEmpty();
    }

    /**
     * El caso de uso no puede construirse sin repositorio: es mejor fallar al arrancar que al
     * atender la primera petición.
     */
    @Test
    void rechaza_un_repositorio_nulo_en_el_constructor() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ConsultarCuentas(null))
                .withMessageContaining("repositorio");
    }

    private static Cuenta cuenta(String nombre) {
        return new Cuenta(UUID.randomUUID(), TipoDeCuenta.AHORROS, nombre, BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1), false, false);
    }

    /** Repositorio en memoria: devuelve {@link #respuesta} y anota cada usuario consultado. */
    private static final class RepositorioFalso implements RepositorioCuentas {

        private List<Cuenta> respuesta = List.of();
        private final List<UUID> usuariosConsultados = new ArrayList<>();

        @Override
        public List<Cuenta> buscarCuentasUsuario(UUID usuarioId) {
            usuariosConsultados.add(usuarioId);
            return respuesta;
        }
    }
}
