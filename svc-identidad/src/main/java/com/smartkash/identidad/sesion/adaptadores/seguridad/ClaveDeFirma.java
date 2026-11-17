package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Clave RSA con la que el servicio firma los tokens de acceso.
 *
 * <p>Qué es: un par de claves. La privada firma y solo la tiene este servicio; la pública
 * comprueba firmas y se puede publicar sin riesgo, para que otros servicios verifiquen los
 * tokens. Con la pública no se puede firmar.</p>
 *
 * <p>De dónde viene: de un archivo PEM con la clave privada en formato PKCS#8 (el que produce
 * {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072}). La ruta del archivo
 * llega por configuración; el archivo vive fuera del repositorio y de la base de datos. La
 * clave pública no se lee de ningún sitio: se calcula a partir de la privada, así que nunca
 * pueden quedar desparejadas.</p>
 *
 * <p>Identificador: cada clave tiene un identificador calculado a partir de su parte pública
 * (su «huella», según el estándar público RFC 7638). Va en la cabecera de cada token para que
 * quien lo compruebe sepa con qué clave pública hacerlo, también cuando un día se cambie de
 * clave.</p>
 *
 * <p>Reglas y errores: lanza {@link IllegalStateException}, y por tanto el servicio no
 * arranca, si el archivo no se puede leer, no es PKCS#8, no es RSA o la clave tiene menos de
 * {@value #BITS_MINIMOS} bits. Los mensajes pueden nombrar la ruta, pero nunca muestran el
 * contenido del archivo.</p>
 */
public final class ClaveDeFirma {

    /** Tamaño mínimo aceptado. Por debajo, la clave se considera débil para firmar a largo plazo. */
    public static final int BITS_MINIMOS = 3072;

    private static final String CABECERA = "-----BEGIN PRIVATE KEY-----";
    private static final String PIE = "-----END PRIVATE KEY-----";

    private final RSAKey jwk;

    private ClaveDeFirma(RSAKey jwk) {
        this.jwk = jwk;
    }

    /**
     * Lee la clave privada de un archivo PEM y prepara la clave completa.
     *
     * @param ruta ruta del archivo
     * @return la clave lista para firmar
     * @throws IllegalStateException si el archivo no sirve; el mensaje explica por qué
     */
    public static ClaveDeFirma desdeArchivo(Path ruta) {
        String texto;
        try {
            texto = Files.readString(ruta, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo leer el archivo de la clave de firma: " + ruta, e);
        }

        byte[] der = decodificarPem(texto);
        try {
            RSAPrivateCrtKey privada = leerPrivada(der);
            return new ClaveDeFirma(armar(privada));
        } finally {
            // Los bytes de la clave ya no hacen falta: se borran para no dejarlos en memoria.
            Arrays.fill(der, (byte) 0);
        }
    }

    /**
     * Devuelve el identificador de la clave, el mismo que va en la cabecera de los tokens.
     *
     * @return la huella de la parte pública
     */
    public String id() {
        return jwk.getKeyID();
    }

    /**
     * Devuelve la parte pública, la que sirve para comprobar firmas.
     *
     * @return la clave pública RSA
     */
    public RSAPublicKey clavePublica() {
        try {
            return jwk.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo obtener la clave pública.", e);
        }
    }

    /**
     * Devuelve la parte pública en formato JWKS, listo para publicarse.
     *
     * <p>Qué es JWKS: un formato estándar (RFC 7517) para publicar claves públicas como JSON:
     * un objeto con una lista {@code keys}. Cada clave lleva su tipo ({@code kty}), su uso
     * ({@code use=sig}, firmar), su algoritmo, su identificador ({@code kid}) y los dos números
     * de la parte pública RSA ({@code n} y {@code e}).</p>
     *
     * <p>Regla: se construye a partir de la clave pública ({@code toPublicJWK}), así que es
     * imposible que lleve ningún dato privado.</p>
     *
     * @return el conjunto de claves públicas como mapa, que Spring convierte en JSON
     */
    public Map<String, Object> conjuntoPublico() {
        return new JWKSet(jwk.toPublicJWK()).toJSONObject();
    }

    /**
     * Devuelve la clave completa en el formato de la librería Nimbus, para quien firma.
     *
     * <p>Solo es visible dentro de este paquete: incluye la parte privada.</p>
     *
     * @return la clave con su parte privada
     */
    RSAKey paraFirmar() {
        return jwk;
    }

    /**
     * Devuelve un texto corto para depuración, con el identificador y nunca la clave.
     *
     * @return texto con el identificador
     */
    @Override
    public String toString() {
        return "ClaveDeFirma[id=" + id() + "]";
    }

    /*
     * Saca del texto PEM el contenido binario. Rechaza con un mensaje útil los formatos
     * parecidos que no se admiten, sin repetir nunca el contenido.
     */
    private static byte[] decodificarPem(String texto) {
        if (texto.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("La clave de firma está en formato PKCS#1; se espera "
                    + "PKCS#8. Conviértela con: openssl pkcs8 -topk8 -nocrypt -in <actual> -out <nueva>");
        }
        if (texto.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            throw new IllegalStateException(
                    "La clave de firma está protegida con contraseña; se espera PKCS#8 sin cifrar.");
        }
        int inicio = texto.indexOf(CABECERA);
        int fin = texto.indexOf(PIE);
        if (inicio < 0 || fin < inicio) {
            throw new IllegalStateException("El archivo de la clave de firma no tiene el formato "
                    + "esperado: PEM con PKCS#8 (" + CABECERA + ").");
        }
        String base64 = texto.substring(inicio + CABECERA.length(), fin).replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("El contenido de la clave de firma no es Base64 válido.");
        }
    }

    /*
     * Interpreta los bytes como clave privada RSA. No se encadena la excepción original:
     * así ningún detalle del contenido puede acabar en un log.
     */
    private static RSAPrivateCrtKey leerPrivada(byte[] der) {
        PrivateKey clave;
        try {
            clave = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "El archivo no contiene una clave privada RSA válida en formato PKCS#8.");
        }
        // CRT es la forma habitual de guardar una clave RSA: además de la parte secreta incluye
        // el exponente público, que hace falta para calcular la clave pública.
        if (!(clave instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException(
                    "La clave RSA no incluye los datos necesarios para calcular su clave pública.");
        }
        int bits = crt.getModulus().bitLength();
        if (bits < BITS_MINIMOS) {
            throw new IllegalStateException("La clave de firma tiene " + bits
                    + " bits; se exigen al menos " + BITS_MINIMOS + ".");
        }
        return crt;
    }

    /* Calcula la clave pública y arma la clave completa con su identificador. */
    private static RSAKey armar(RSAPrivateCrtKey privada) {
        try {
            RSAPublicKey publica = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                    new RSAPublicKeySpec(privada.getModulus(), privada.getPublicExponent()));
            return new RSAKey.Builder(publica)
                    .privateKey(privada)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .keyIDFromThumbprint()
                    .build();
        } catch (GeneralSecurityException | JOSEException e) {
            throw new IllegalStateException("No se pudo preparar la clave de firma.");
        }
    }
}