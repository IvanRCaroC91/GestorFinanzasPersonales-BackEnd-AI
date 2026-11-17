package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.identidad.ClavesDePrueba;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba la lectura de la clave privada con la que se firman los tokens.
 *
 * <p>Qué hace: genera claves en memoria, las escribe en archivos temporales y las lee como lo
 * hará el servicio al arrancar. Comprueba que una clave válida se acepta y que su clave
 * pública se calcula bien, y que cualquier archivo inválido se rechaza con un mensaje claro
 * que nunca muestra el contenido del archivo. No necesita Spring ni Docker.</p>
 */
class ClaveDeFirmaTest {

    private static final KeyPair PAR_3072 = ClavesDePrueba.rsa(3072);

    /** Una clave RSA de 3072 bits en PKCS#8 se acepta y su clave pública es la del par. */
    @Test
    void lee_la_clave_privada_y_calcula_su_clave_publica() {
        ClaveDeFirma clave = ClaveDeFirma.desdeArchivo(archivoCon(PAR_3072));

        RSAPublicKey esperada = (RSAPublicKey) PAR_3072.getPublic();
        assertThat(clave.clavePublica().getModulus()).isEqualTo(esperada.getModulus());
        assertThat(clave.clavePublica().getPublicExponent()).isEqualTo(esperada.getPublicExponent());
    }

    /**
     * El identificador de la clave se calcula a partir de ella misma: leer dos veces la misma
     * clave da el mismo identificador y otra clave da otro.
     */
    @Test
    void el_identificador_depende_solo_de_la_clave() {
        Path archivo = archivoCon(PAR_3072);

        String id = ClaveDeFirma.desdeArchivo(archivo).id();

        assertThat(id).isNotBlank();
        assertThat(ClaveDeFirma.desdeArchivo(archivo).id()).isEqualTo(id);
        assertThat(ClaveDeFirma.desdeArchivo(archivoCon(ClavesDePrueba.rsa(3072))).id()).isNotEqualTo(id);
    }

    /** Una clave de 2048 bits es demasiado corta y se rechaza. */
    @Test
    void rechaza_una_clave_de_menos_de_3072_bits() {
        Path archivo = archivoCon(ClavesDePrueba.rsa(2048));

        assertThatThrownBy(() -> ClaveDeFirma.desdeArchivo(archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("3072");
    }

    /** Si el archivo no existe, el error lo dice y nombra la ruta, que no es secreta. */
    @Test
    void rechaza_un_archivo_que_no_existe() {
        Path inexistente = Path.of("no-existe", "clave.pem");

        assertThatThrownBy(() -> ClaveDeFirma.desdeArchivo(inexistente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo leer")
                .hasMessageContaining(inexistente.toString());
    }

    /** Un archivo que no es una clave se rechaza sin copiar su contenido en el mensaje. */
    @Test
    void rechaza_un_contenido_que_no_es_una_clave_sin_mostrarlo() {
        String contenido = "-----BEGIN PRIVATE KEY-----\nZXN0byBubyBlcyB1bmEgY2xhdmU=\n-----END PRIVATE KEY-----\n";
        Path archivo = ClavesDePrueba.archivoTemporal(contenido);

        assertThatThrownBy(() -> ClaveDeFirma.desdeArchivo(archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("ZXN0byBubyBlcyB1bmEgY2xhdmU");
    }

    /**
     * El formato antiguo de OpenSSL (PKCS#1, cabecera {@code BEGIN RSA PRIVATE KEY}) no se
     * admite; el mensaje explica qué formato se espera.
     */
    @Test
    void rechaza_el_formato_pkcs1_y_explica_cual_se_espera() {
        String pkcs1 = ClavesDePrueba.pem(PAR_3072.getPrivate())
                .replace("BEGIN PRIVATE KEY", "BEGIN RSA PRIVATE KEY")
                .replace("END PRIVATE KEY", "END RSA PRIVATE KEY");
        Path archivo = ClavesDePrueba.archivoTemporal(pkcs1);

        assertThatThrownBy(() -> ClaveDeFirma.desdeArchivo(archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }

    /** Una clave de otro algoritmo (curva elíptica) se rechaza: el servicio firma con RSA. */
    @Test
    void rechaza_una_clave_que_no_es_rsa() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("EC");
        generador.initialize(new ECGenParameterSpec("secp256r1"));
        Path archivo = archivoCon(generador.generateKeyPair());

        assertThatThrownBy(() -> ClaveDeFirma.desdeArchivo(archivo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RSA");
    }

    /**
     * El conjunto público de claves lleva una sola clave RSA para firmar con RS256, con su
     * identificador y su parte pública (módulo {@code n} y exponente {@code e}), y ninguno de
     * los datos privados que define el estándar ({@code d}, {@code p}, {@code q}, {@code dp},
     * {@code dq}, {@code qi}, {@code oth}).
     */
    @Test
    @SuppressWarnings("unchecked")
    void el_conjunto_publico_no_contiene_la_parte_privada() {
        ClaveDeFirma clave = ClaveDeFirma.desdeArchivo(archivoCon(PAR_3072));

        Map<String, Object> conjunto = clave.conjuntoPublico();

        assertThat(conjunto).containsOnlyKeys("keys");
        List<Map<String, Object>> claves = (List<Map<String, Object>>) conjunto.get("keys");
        assertThat(claves).hasSize(1);
        assertThat(claves.getFirst())
                .containsEntry("kty", "RSA")
                .containsEntry("use", "sig")
                .containsEntry("alg", "RS256")
                .containsEntry("kid", clave.id())
                .containsKeys("n", "e")
                .doesNotContainKeys("d", "p", "q", "dp", "dq", "qi", "oth");
        String exponentePrivado = clave.paraFirmar().getPrivateExponent().toString();
        assertThat(conjunto.toString()).doesNotContain(exponentePrivado);
    }

    /** El texto de depuración muestra el identificador, nunca la clave. */
    @Test
    void el_texto_de_depuracion_no_muestra_la_clave() {
        ClaveDeFirma clave = ClaveDeFirma.desdeArchivo(archivoCon(PAR_3072));
        String modulo = ((RSAPublicKey) PAR_3072.getPublic()).getModulus().toString();

        assertThat(clave.toString())
                .contains(clave.id())
                .doesNotContain(modulo)
                .doesNotContain(ClavesDePrueba.pem(PAR_3072.getPrivate()).substring(28, 92));
    }

    private static Path archivoCon(KeyPair par) {
        return ClavesDePrueba.archivoTemporal(ClavesDePrueba.pem(par.getPrivate()));
    }
}