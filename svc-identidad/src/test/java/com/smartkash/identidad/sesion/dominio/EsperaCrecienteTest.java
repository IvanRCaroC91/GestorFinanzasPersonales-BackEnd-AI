package com.smartkash.identidad.sesion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link EsperaCreciente}.
 *
 * <p>Qué comprueban: que la espera empieza en 1 minuto al alcanzar el límite de fallos, se
 * duplica con cada fallo adicional y nunca pasa de 15 minutos.</p>
 *
 * <p>Son pruebas de dominio: no arrancan Spring ni tocan la base de datos.</p>
 */
class EsperaCrecienteTest {

    @ParameterizedTest(name = "con {0} fallos de más se esperan {1} minutos")
    @CsvSource({
            "0, 1",
            "1, 2",
            "2, 4",
            "3, 8",
            "4, 15",
            "5, 15"
    })
    @DisplayName("la espera se duplica con cada fallo de más y se detiene en el tope")
    void laEsperaSeDuplicaHastaElTope(int fallosDeMas, long minutos) {
        assertEquals(Duration.ofMinutes(minutos), EsperaCreciente.tras(fallosDeMas));
    }

    @Test
    @DisplayName("con muchísimos fallos de más la espera sigue siendo el tope")
    void conMuchisimosFallosSigueEnElTope() {
        // Duplicar mil veces desbordaría cualquier número; el resultado debe seguir siendo 15.
        assertEquals(Duration.ofMinutes(15), EsperaCreciente.tras(1_000));
        assertEquals(Duration.ofMinutes(15), EsperaCreciente.tras(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("rechaza una cantidad negativa de fallos de más")
    void rechazaCantidadNegativa() {
        assertThrows(IllegalArgumentException.class, () -> EsperaCreciente.tras(-1));
    }
}