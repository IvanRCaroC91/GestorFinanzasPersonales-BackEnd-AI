package com.smartkash.identidad;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

/**
 * Fabrica claves RSA de usar y tirar para las pruebas.
 *
 * <p>Qué hace: genera pares de claves en memoria y los escribe en archivos temporales con el
 * mismo formato que usará el servicio en la realidad (PEM con PKCS#8, el que produce
 * {@code openssl genpkey}). Así ninguna prueba necesita una clave guardada en el
 * repositorio.</p>
 */
public final class ClavesDePrueba {

    private ClavesDePrueba() {
    }

    /**
     * Genera un par de claves RSA nuevo.
     *
     * @param bits tamaño de la clave
     * @return el par de claves, pública y privada
     */
    public static KeyPair rsa(int bits) {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(bits);
            return generador.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Escribe una clave privada en formato PEM con PKCS#8.
     *
     * <p>PEM es texto: una línea de inicio, el contenido binario en Base64 partido en líneas
     * de 64 caracteres y una línea de fin.</p>
     *
     * @param clave la clave privada
     * @return el texto PEM
     */
    public static String pem(PrivateKey clave) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(clave.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    /**
     * Escribe un texto en un archivo temporal que se borra al terminar las pruebas.
     *
     * @param contenido lo que se escribe
     * @return la ruta del archivo
     */
    public static Path archivoTemporal(String contenido) {
        try {
            Path archivo = Files.createTempFile("clave-de-prueba-", ".pem");
            archivo.toFile().deleteOnExit();
            Files.writeString(archivo, contenido, StandardCharsets.US_ASCII);
            return archivo;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}