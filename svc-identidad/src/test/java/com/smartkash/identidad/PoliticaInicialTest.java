package com.smartkash.identidad;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el texto de la política de tratamiento de datos es exactamente el que la base
 * de datos dice que es.
 *
 * <p>Qué hace: calcula la huella SHA-256 del archivo con el texto de la política y la compara
 * con la huella escrita en la migración que publica esa versión. Una huella (o hash) es un
 * resumen de tamaño fijo: si cambia un solo byte del texto, la huella cambia por completo.</p>
 *
 * <p>De dónde vienen los datos: de dos archivos del propio servicio, leídos tal como quedan
 * empaquetados: {@code politicas/politica-tratamiento-1.0.md} y
 * {@code db/migration/V2__politica_inicial.sql}. No van a ningún sitio; solo se comparan.</p>
 *
 * <p>Por qué importa: la huella guardada es la prueba de qué texto aceptó cada persona al
 * registrarse. Si el archivo cambiara sin publicar una versión nueva (por una edición o porque
 * Git convierte los saltos de línea al descargar en Windows), esa prueba dejaría de valer.</p>
 *
 * <p>No necesita Spring, base de datos ni Docker.</p>
 */
class PoliticaInicialTest {

    private static final String TEXTO_POLITICA = "/politicas/politica-tratamiento-1.0.md";
    private static final String MIGRACION = "/db/migration/V2__politica_inicial.sql";

    /* Una huella SHA-256 en hexadecimal son 64 caracteres; en la migración va entre comillas simples. */
    private static final Pattern HUELLA_ENTRE_COMILLAS = Pattern.compile("'([0-9a-f]{64})'");

    /**
     * El archivo debe usar saltos de línea de un solo carácter (LF). La huella guardada se
     * calculó así; con saltos de Windows (CRLF) los bytes serían otros.
     *
     * @throws IOException si no se puede leer el archivo
     */
    @Test
    void el_texto_de_la_politica_usa_saltos_de_linea_lf() throws IOException {
        byte[] texto = leer(TEXTO_POLITICA);

        /* Se cuentan en vez de comparar el contenido para que el fallo muestre un número y no todo el archivo. */
        int retornosDeCarro = 0;
        for (byte b : texto) {
            if (b == '\r') {
                retornosDeCarro++;
            }
        }

        assertThat(retornosDeCarro)
                .as("Retornos de carro en el texto de la política; revisa .gitattributes")
                .isZero();
    }

    /**
     * La huella del archivo debe ser la misma que la migración guarda en la base de datos.
     *
     * @throws IOException si no se puede leer alguno de los dos archivos
     * @throws NoSuchAlgorithmException si la máquina virtual no ofrece SHA-256 (no debería pasar:
     *         toda instalación de Java lo incluye)
     */
    @Test
    void la_huella_del_texto_coincide_con_la_de_la_migracion() throws IOException, NoSuchAlgorithmException {
        String huellaGuardada = huellaEscritaEnLaMigracion();

        byte[] resumen = MessageDigest.getInstance("SHA-256").digest(leer(TEXTO_POLITICA));
        String huellaDelArchivo = HexFormat.of().formatHex(resumen);

        assertThat(huellaDelArchivo)
                .as("El texto de la política cambió sin publicar una versión nueva")
                .isEqualTo(huellaGuardada);
    }

    /*
     * Saca la huella directamente del SQL de la migración en lugar de repetirla aquí: así hay
     * una sola copia del valor y la prueba compara contra lo que de verdad llega a la base.
     */
    private static String huellaEscritaEnLaMigracion() throws IOException {
        String sql = new String(leer(MIGRACION), StandardCharsets.UTF_8);
        Matcher coincidencia = HUELLA_ENTRE_COMILLAS.matcher(sql);

        assertThat(coincidencia.find())
                .as("La migración debe contener una huella SHA-256 entre comillas")
                .isTrue();
        return coincidencia.group(1);
    }

    /* Lee todos los bytes de un archivo del servicio, sin interpretar ni convertir nada. */
    private static byte[] leer(String ruta) throws IOException {
        try (InputStream entrada = PoliticaInicialTest.class.getResourceAsStream(ruta)) {
            assertThat(entrada).as("No se encontró el archivo %s", ruta).isNotNull();
            return entrada.readAllBytes();
        }
    }
}