package com.smartkash.finanzas.seguridad;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.smartkash.finanzas.TokensDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static com.smartkash.finanzas.TokensDePrueba.EMISOR;
import static com.smartkash.finanzas.TokensDePrueba.datosValidos;
import static com.smartkash.finanzas.TokensDePrueba.firmar;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba las reglas con las que el servicio acepta o rechaza un token de acceso.
 *
 * <p>Qué hace: toma el validador con las reglas del servicio
 * ({@link ConfiguracionDeSeguridad#conValidaciones}) y le da tokens firmados en la propia prueba
 * con una clave local, uno correcto y otros estropeados de una sola forma cada uno. No arranca
 * Spring ni descarga nada, así que es rápida y no necesita Docker.</p>
 *
 * <p>Por qué cada rechazo comprueba también el motivo: un token puede rechazarse por un error
 * distinto del que la prueba quiere provocar (por ejemplo, una firma mal hecha en la propia
 * prueba). Si solo se mirara que hay rechazo, la prueba pasaría aunque la regla que se quiere
 * comprobar no existiera.</p>
 *
 * <p>Lo que aquí no se prueba: que solo se acepte el algoritmo RS256 al descargar las claves.
 * Esa parte depende de la dirección de descarga y se comprueba en
 * {@code RecorridoConTokenRealTest}, con un servidor de claves de verdad.</p>
 */
class ValidadorDeTokensTest {

    private static final RSAKey CLAVE = TokensDePrueba.claveNueva();

    private final JwtDecoder validador = ConfiguracionDeSeguridad.conValidaciones(
            NimbusJwtDecoder.withPublicKey(clavePublica(CLAVE)).build(), EMISOR);

    private final UUID usuario = UUID.randomUUID();
    private final Instant ahora = Instant.now();

    // ---------- Lo que se acepta ----------

    /**
     * Un token como los que emite identidad se acepta, y de él se leen la persona y sus roles.
     */
    @Test
    void acepta_un_token_correcto() {
        Jwt token = validador.decode(firmar(CLAVE, datosValidos(usuario, ahora).build()));

        assertThat(token.getSubject()).isEqualTo(usuario.toString());
        assertThat(token.getClaimAsStringList("roles")).containsExactly("USUARIO");
    }

    /**
     * Un token caducado hace 30 segundos todavía se acepta: hay un margen de 60 segundos por si
     * el reloj de este servidor y el de identidad no marcan exactamente la misma hora.
     */
    @Test
    void acepta_un_token_caducado_dentro_del_margen_entre_relojes() {
        JWTClaimsSet datos = datosValidos(usuario, ahora.minusSeconds(900))
                .expirationTime(Date.from(ahora.minusSeconds(30)))
                .build();

        assertThat(validador.decode(firmar(CLAVE, datos)).getSubject()).isEqualTo(usuario.toString());
    }

    // ---------- Fechas ----------

    /** Un token caducado hace dos minutos, fuera del margen, se rechaza. */
    @Test
    void rechaza_un_token_caducado() {
        JWTClaimsSet datos = datosValidos(usuario, ahora.minusSeconds(1020))
                .expirationTime(Date.from(ahora.minusSeconds(120)))
                .build();

        assertThatThrownBy(() -> validador.decode(firmar(CLAVE, datos)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    /**
     * Un token sin fecha de caducidad se rechaza. Si se aceptara, valdría para siempre: un
     * token así filtrado una vez daría acceso indefinido.
     */
    @Test
    void rechaza_un_token_sin_caducidad() {
        JWTClaimsSet datos = datosValidos(usuario, ahora).expirationTime(null).build();

        assertThatThrownBy(() -> validador.decode(firmar(CLAVE, datos)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("exp is required");
    }

    // ---------- Emisor y destinatario ----------

    /** Un token emitido por otro servidor se rechaza aunque esté bien firmado. */
    @Test
    void rechaza_un_token_de_otro_emisor() {
        JWTClaimsSet datos = datosValidos(usuario, ahora).issuer("https://otro-emisor.invalid").build();

        assertThatThrownBy(() -> validador.decode(firmar(CLAVE, datos)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    /**
     * Un token dirigido a otro destinatario se rechaza. Evita que un token pensado para otra
     * aplicación que confíe en la misma identidad sirva aquí.
     */
    @Test
    void rechaza_un_token_para_otro_destinatario() {
        JWTClaimsSet datos = datosValidos(usuario, ahora).audience("otra-aplicacion").build();

        assertThatThrownBy(() -> validador.decode(firmar(CLAVE, datos)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("aud");
    }

    /** Un token sin destinatario se rechaza: el destinatario es obligatorio, no opcional. */
    @Test
    void rechaza_un_token_sin_destinatario() {
        JWTClaimsSet datos = datosValidos(usuario, ahora).audience((String) null).build();

        assertThatThrownBy(() -> validador.decode(firmar(CLAVE, datos)))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("aud");
    }

    // ---------- Firma ----------

    /**
     * Un token firmado con otra clave se rechaza, aunque sus datos sean perfectos: es lo que
     * haría quien intente fabricarse un token sin tener la clave de identidad.
     */
    @Test
    void rechaza_un_token_firmado_con_otra_clave() {
        String token = firmar(TokensDePrueba.claveNueva(), datosValidos(usuario, ahora).build());

        assertThatThrownBy(() -> validador.decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("signature");
    }

    /**
     * Un token sin firma (algoritmo {@code none}) se rechaza. Es un ataque clásico: quitar la
     * firma y esperar que el servidor se fíe de los datos.
     */
    @Test
    void rechaza_un_token_sin_firma() {
        String token = new PlainJWT(datosValidos(usuario, ahora).build()).serialize();

        assertThatThrownBy(() -> validador.decode(token))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("Unsupported algorithm");
    }

    /**
     * Si alguien cambia los datos de un token ya firmado (aquí, pone el identificador de otra
     * persona) la firma deja de coincidir y se rechaza.
     */
    @Test
    void rechaza_un_token_con_los_datos_cambiados_despues_de_firmar() {
        String[] partes = firmar(CLAVE, datosValidos(usuario, ahora).build()).split("\\.");
        String otrosDatos = datosValidos(UUID.randomUUID(), ahora).build().toString();
        String alterado = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(otrosDatos.getBytes(UTF_8))
                + "." + partes[2];

        assertThatThrownBy(() -> validador.decode(alterado))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("signature");
    }

    private static java.security.interfaces.RSAPublicKey clavePublica(RSAKey clave) {
        try {
            return clave.toRSAPublicKey();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
