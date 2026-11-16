package com.smartkash.identidad.registro.adaptadores.seguridad;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la lista de contraseñas comunes que se lee de un archivo.
 *
 * <p>Qué hace: primero prueba el comportamiento de la clase con un archivo pequeño de
 * ejemplo, y después revisa que el archivo real que acompaña al servicio está bien formado.</p>
 *
 * <p>De dónde vienen los datos: de {@code seguridad/contrasenas-de-ejemplo.txt} (solo para
 * pruebas) y de {@code seguridad/contrasenas-comunes.txt} (el real). No necesita Spring ni
 * Docker.</p>
 */
class ContrasenasComunesDeArchivoTest {

    private static final String EJEMPLO = "/seguridad/contrasenas-de-ejemplo.txt";

    private final ContrasenasComunesDeArchivo deEjemplo = new ContrasenasComunesDeArchivo(EJEMPLO);

    /** Una contraseña que está en el archivo figura como común. */
    @Test
    void reconoce_una_contrasena_del_archivo() {
        assertThat(deEjemplo.contiene("contrasena-muy-usada")).isTrue();
    }

    /** Una contraseña que no está en el archivo no figura como común. */
    @Test
    void no_reconoce_una_contrasena_que_no_esta() {
        assertThat(deEjemplo.contiene("frase larga que nadie mas usa")).isFalse();
    }

    /**
     * Quien pregunta entrega la contraseña en minúsculas, así que las entradas del archivo
     * escritas con mayúsculas se guardan también en minúsculas para que coincidan.
     */
    @Test
    void guarda_las_entradas_en_minusculas() {
        assertThat(deEjemplo.contiene("otraconmayusculas123")).isTrue();
    }

    /** Los espacios forman parte de la contraseña: no se recortan ni se quitan. */
    @Test
    void respeta_los_espacios_de_cada_entrada() {
        assertThat(deEjemplo.contiene("con espacio dentro")).isTrue();
        assertThat(deEjemplo.contiene("conespaciodentro")).isFalse();
    }

    /** Una línea vacía del archivo no convierte la contraseña vacía en una entrada. */
    @Test
    void ignora_las_lineas_vacias() {
        assertThat(deEjemplo.contiene("")).isFalse();
        assertThat(deEjemplo.cantidad()).isEqualTo(3);
    }

    /**
     * Si el archivo no existe, el servicio no debe arrancar como si la lista estuviera
     * vacía: aceptaría cualquier contraseña común sin que nadie lo notara.
     */
    @Test
    void falla_si_el_archivo_no_existe() {
        assertThatThrownBy(() -> new ContrasenasComunesDeArchivo("/seguridad/no-existe.txt"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** La lista real se carga y rechaza contraseñas largas pero muy usadas. */
    @Test
    void la_lista_real_reconoce_contrasenas_muy_usadas() {
        ContrasenasComunesDeArchivo real = new ContrasenasComunesDeArchivo();

        assertThat(real.cantidad()).isGreaterThan(1000);
        assertThat(real.contiene("qwerty123456")).isTrue();
        assertThat(real.contiene("q1w2e3r4t5y6")).isTrue();
    }

    /**
     * Cada entrada del archivo real debe poder llegar a consultarse: en minúsculas y con un
     * largo de entre 12 y 64 caracteres. Una entrada más corta o más larga nunca pasaría las
     * reglas de largo de la contraseña, así que solo ocuparía espacio.
     *
     * @throws IOException si no se puede leer el archivo
     */
    @Test
    void la_lista_real_solo_tiene_entradas_utiles() throws IOException {
        List<String> lineas;
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(ContrasenasComunesDeArchivo.ARCHIVO),
                StandardCharsets.UTF_8))) {
            lineas = lector.lines().toList();
        }

        assertThat(lineas).isNotEmpty().doesNotHaveDuplicates().allSatisfy(linea -> {
            assertThat(linea).isEqualTo(linea.toLowerCase(Locale.ROOT));
            assertThat(linea.codePointCount(0, linea.length())).isBetween(12, 64);
        });
    }
}