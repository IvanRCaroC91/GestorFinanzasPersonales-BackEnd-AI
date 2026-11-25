package com.smartkash.finanzas.seguridad;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que la configuración de seguridad no admite datos ausentes ni en blanco.
 *
 * <p>Por qué importa: sin la dirección de las claves o sin el emisor, el servicio no podría
 * validar tokens. Es mejor que no arranque y diga qué variable falta a que arranque y rechace
 * todas las peticiones sin explicar por qué.</p>
 *
 * <p>Qué es {@code @ParameterizedTest}: la misma prueba se ejecuta una vez por cada valor.
 * {@code @NullAndEmptySource} aporta el nulo y el texto vacío, y {@code @ValueSource} los
 * textos con solo espacios.</p>
 */
class PropiedadesDeSeguridadTest {

    private static final String CLAVES = "http://identidad.pruebas.invalid/.well-known/jwks.json";
    private static final String EMISOR = "https://identidad.pruebas.invalid";

    /** Con los dos datos presentes se crea sin cambiarlos. */
    @Test
    void acepta_los_dos_datos_presentes() {
        PropiedadesDeSeguridad propiedades = new PropiedadesDeSeguridad(CLAVES, EMISOR);

        assertThat(propiedades.jwksUri()).isEqualTo(CLAVES);
        assertThat(propiedades.emisor()).isEqualTo(EMISOR);
    }

    /**
     * Sin dirección de claves no se crea, y el mensaje nombra la variable de entorno que hay
     * que definir.
     *
     * @param valor dirección ausente o en blanco
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rechaza_la_direccion_de_claves_ausente_o_en_blanco(String valor) {
        assertThatThrownBy(() -> new PropiedadesDeSeguridad(valor, EMISOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINANZAS_JWKS_URI");
    }

    /**
     * Sin emisor no se crea, y el mensaje nombra la variable de entorno que hay que definir.
     *
     * @param valor emisor ausente o en blanco
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rechaza_el_emisor_ausente_o_en_blanco(String valor) {
        assertThatThrownBy(() -> new PropiedadesDeSeguridad(CLAVES, valor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FINANZAS_TOKEN_EMISOR");
    }
}
