package com.smartkash.finanzas;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Fabrica tokens de acceso de verdad, firmados, para las pruebas de seguridad.
 *
 * <p>Qué hace: genera claves RSA en memoria y firma con ellas tokens con la misma forma que
 * emite el servicio de identidad (emisor, destinatario {@code api}, {@code sub} con el
 * identificador de la persona, {@code roles}, identificador único y fechas). Ninguna clave se
 * guarda en el repositorio: cada ejecución crea las suyas.</p>
 *
 * <p>Quién firma: Nimbus, la librería estándar que Spring Security usa por dentro para leer
 * tokens. Aquí no hay criptografía propia.</p>
 *
 * <p>Qué es un token firmado (JWT): tres partes en Base64 separadas por puntos. La cabecera
 * dice el algoritmo y la clave usada ({@code kid}), el cuerpo trae los datos y la firma prueba
 * que nadie los cambió. Quien tiene la clave pública puede comprobar la firma, pero solo quien
 * tiene la privada puede firmar.</p>
 */
public final class TokensDePrueba {

    /** Emisor inventado; es el mismo que {@link EntornoDePruebas} entrega al servicio. */
    public static final String EMISOR = "https://identidad.pruebas.invalid";

    /** Destinatario que exige el servicio, igual al que pone el servicio de identidad. */
    public static final String DESTINATARIO = "api";

    /** Vida de un token de acceso emitido por identidad. */
    private static final Duration VIGENCIA = Duration.ofMinutes(15);

    private TokensDePrueba() {
    }

    /**
     * Genera una clave RSA de 2048 bits con un identificador al azar.
     *
     * @return la clave, con su parte pública y su parte privada
     */
    public static RSAKey claveNueva() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Prepara los datos de un token correcto: los que emitiría identidad para esa persona.
     * Cada prueba cambia solo el dato que quiere estropear.
     *
     * @param usuario identificador de la persona, que va en {@code sub}
     * @param ahora   momento de la emisión
     * @return los datos, listos para modificarse y firmarse
     */
    public static JWTClaimsSet.Builder datosValidos(UUID usuario, Instant ahora) {
        return new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .audience(DESTINATARIO)
                .subject(usuario.toString())
                .claim("roles", List.of("USUARIO"))
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plus(VIGENCIA)));
    }

    /**
     * Firma unos datos con RS256, el algoritmo que usa identidad.
     *
     * @param clave clave privada con la que se firma; su identificador va en la cabecera
     * @param datos lo que lleva el token
     * @return el token en texto, listo para la cabecera {@code Authorization}
     */
    public static String firmar(RSAKey clave, JWTClaimsSet datos) {
        return firmar(clave, JWSAlgorithm.RS256, datos);
    }

    /**
     * Firma unos datos con el algoritmo indicado. Sirve para comprobar que el servicio rechaza
     * los algoritmos que no acepta aunque la clave sea la buena.
     *
     * @param clave     clave privada con la que se firma
     * @param algoritmo algoritmo de firma RSA
     * @param datos     lo que lleva el token
     * @return el token en texto
     */
    public static String firmar(RSAKey clave, JWSAlgorithm algoritmo, JWTClaimsSet datos) {
        try {
            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(algoritmo).keyID(clave.getKeyID()).build(), datos);
            token.sign(new RSASSASigner(clave));
            return token.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
