package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.dominio.TokenAcceso;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Firma los tokens de acceso como JWT con el algoritmo RS256.
 *
 * <p>Qué es un JWT: un texto en tres partes separadas por puntos. La primera (cabecera) dice
 * cómo se firmó; la segunda (contenido) lleva los datos, llamados «claims»; la tercera es la
 * firma de las dos primeras. Las dos primeras solo están codificadas, no cifradas: cualquiera
 * puede leerlas. Lo que nadie puede hacer sin la clave privada es cambiarlas, porque la firma
 * dejaría de cuadrar. Por eso el token no lleva datos personales.</p>
 *
 * <p>Qué es RS256: firma RSA con resumen SHA-256. Se firma con la clave privada y se
 * comprueba con la pública, así que otros servicios pueden comprobar tokens sin poder
 * fabricarlos.</p>
 *
 * <p>Qué lleva cada token:</p>
 * <ul>
 *   <li>{@code iss}: quién lo emitió, tomado de la configuración.</li>
 *   <li>{@code aud}: a quién va dirigido, siempre {@value #DESTINATARIO}.</li>
 *   <li>{@code sub}: el identificador de la cuenta. Es de aquí de donde los demás servicios
 *       sacarán siempre el dueño de los datos.</li>
 *   <li>{@code roles}: lo que la cuenta puede hacer.</li>
 *   <li>{@code jti}: un identificador único del token, al azar.</li>
 *   <li>{@code iat} y {@code exp}: cuándo se emitió y cuándo caduca, 15 minutos después.</li>
 * </ul>
 *
 * <p>Quién firma de verdad: {@code NimbusJwtEncoder}, de Spring Security, que usa la librería
 * estándar Nimbus. Aquí no hay criptografía propia.</p>
 */
class EmisorJwtRs256 implements EmisorTokenAcceso {

    /** Destinatario de todos los tokens de acceso. */
    static final String DESTINATARIO = "api";

    private final JwtEncoder firmador;
    private final String idClave;
    private final String emisor;

    /**
     * Prepara el firmador con la clave del servicio.
     *
     * @param clave  la clave de firma, ya leída y comprobada
     * @param emisor valor de {@code iss}
     * @throws NullPointerException si falta alguno
     */
    EmisorJwtRs256(ClaveDeFirma clave, String emisor) {
        Objects.requireNonNull(clave, "La clave de firma es obligatoria.");
        this.emisor = Objects.requireNonNull(emisor, "El emisor es obligatorio.");
        this.idClave = clave.id();
        this.firmador = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(clave.paraFirmar())));
    }

    /**
     * Emite un token de acceso firmado para una cuenta.
     *
     * @param usuario la cuenta que inició sesión
     * @param ahora   momento de la emisión
     * @return el token firmado y su caducidad
     */
    @Override
    public TokenAcceso emitir(Usuario usuario, Instant ahora) {
        Instant expiraEn = ahora.plus(TokenAcceso.VIGENCIA);

        // El algoritmo se fija aquí, en el servidor: nunca se acepta uno que venga de fuera.
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(idClave).build();
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer(emisor)
                .audience(List.of(DESTINATARIO))
                .subject(usuario.id().toString())
                .claim("roles", List.of(usuario.rol().name()))
                .id(UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiresAt(expiraEn)
                .build();

        String valor = firmador.encode(JwtEncoderParameters.from(cabecera, datos)).getTokenValue();
        return new TokenAcceso(valor, expiraEn);
    }
}