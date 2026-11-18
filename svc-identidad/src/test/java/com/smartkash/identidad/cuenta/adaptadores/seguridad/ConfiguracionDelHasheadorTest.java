package com.smartkash.identidad.cuenta.adaptadores.seguridad;

import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el servicio usa el hasheador con tope, y con qué valores.
 *
 * <p>Qué hace: {@link ApplicationContextRunner} arranca un contexto de Spring mínimo, solo con
 * la clase de configuración del hasheador, y deja mirar qué bean quedó registrado. Sin esta
 * prueba, alguien podría registrar Argon2 directamente y el tope dejaría de aplicarse sin que
 * ninguna otra prueba lo notara.</p>
 */
class ConfiguracionDelHasheadorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(ConfiguracionDelHasheador.class);

    /**
     * El único hasheador del servicio es el que tiene tope, con un turno por núcleo y 2 segundos
     * de espera, y por dentro calcula con Argon2.
     */
    @Test
    void el_hasheador_del_servicio_tiene_tope() {
        contexto.run(arrancado -> {
            assertThat(arrancado).hasSingleBean(HasheadorContrasena.class);
            HasheadorContrasena hasheador = arrancado.getBean(HasheadorContrasena.class);

            assertThat(hasheador).isInstanceOf(HasheadorConTope.class);
            HasheadorConTope conTope = (HasheadorConTope) hasheador;
            assertThat(conTope.turnos()).isEqualTo(Runtime.getRuntime().availableProcessors());
            assertThat(conTope.esperaMaxima()).isEqualTo(Duration.ofSeconds(2));
            assertThat(conTope.hashear("una frase larga y dificil 2026")).startsWith("$argon2id$");
        });
    }
}
