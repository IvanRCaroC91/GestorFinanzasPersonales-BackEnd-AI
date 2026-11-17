package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.identidad.ClavesDePrueba;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.dominio.TokenAcceso;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el token de acceso que firma el servicio.
 *
 * <p>Qué hace: firma tokens con una clave generada para la prueba y los comprueba con
 * {@code NimbusJwtDecoder}, el mismo validador estándar que usará el servicio para aceptar
 * tokens. Si la firma no fuera válida, la decodificación fallaría. Después revisa uno a uno
 * los datos que lleva el token y que no lleve ningún dato personal. No necesita Spring ni
 * Docker.</p>
 *
 * <p>La hora de emisión es la actual: el validador rechaza tokens caducados, así que con una
 * hora fija del pasado estas pruebas dejarían de pasar con el tiempo.</p>
 */
class EmisorJwtRs256Test {

    private static final String EMISOR = "https://identidad.pruebas.invalid";
    private static final String CORREO = "persona.inconfundible@correo.com";
    private static final String NOMBRE = "Nombre Inconfundible";

    private static final ClaveDeFirma CLAVE = ClaveDeFirma.desdeArchivo(ClavesDePrueba.archivoTemporal(
            ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate())));

    private final EmisorJwtRs256 emisor = new EmisorJwtRs256(CLAVE, EMISOR);
    private final NimbusJwtDecoder validador = NimbusJwtDecoder.withPublicKey(CLAVE.clavePublica()).build();

    private final Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final Usuario cuenta = new Usuario(UUID.randomUUID(), NOMBRE, CORREO,
            new CorreoNormalizado(CORREO), "hash-cualquiera", Rol.USUARIO, EstadoCuenta.ACTIVA);

    /** La firma se comprueba con la clave pública y la cabecera declara RS256 y la clave usada. */
    @Test
    void el_token_esta_firmado_con_rs256_y_la_clave_del_servicio() {
        Jwt jwt = validador.decode(emisor.emitir(cuenta, ahora).valor());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
        assertThat(jwt.getHeaders()).containsEntry("kid", CLAVE.id());
    }

    /** El token lleva emisor, destinatario, dueño, roles, identificador y fechas. */
    @Test
    void el_token_lleva_los_datos_acordados() {
        Jwt jwt = validador.decode(emisor.emitir(cuenta, ahora).valor());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo(EMISOR);
        assertThat(jwt.getAudience()).containsExactly("api");
        assertThat(jwt.getSubject()).isEqualTo(cuenta.id().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USUARIO");
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getIssuedAt()).isEqualTo(ahora);
        assertThat(jwt.getExpiresAt()).isEqualTo(ahora.plus(Duration.ofMinutes(15)));
    }

    /** No lleva ningún dato más que los acordados. */
    @Test
    void el_token_no_lleva_datos_de_mas() {
        Jwt jwt = validador.decode(emisor.emitir(cuenta, ahora).valor());

        assertThat(jwt.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "aud", "sub", "roles", "jti", "iat", "exp");
    }

    /**
     * El contenido del token se puede leer sin la clave (solo está firmado, no cifrado). Por
     * eso se comprueba sobre el texto en bruto que no aparecen ni el correo ni el nombre.
     */
    @Test
    void el_token_no_contiene_datos_personales() {
        String valor = emisor.emitir(cuenta, ahora).valor();
        String contenido = new String(Base64.getUrlDecoder().decode(valor.split("\\.")[1]),
                StandardCharsets.UTF_8);

        assertThat(contenido).doesNotContain(CORREO).doesNotContain(NOMBRE);
    }

    /** Cada token tiene su propio identificador, aunque se emitan en el mismo instante. */
    @Test
    void cada_token_tiene_un_identificador_distinto() {
        Jwt primero = validador.decode(emisor.emitir(cuenta, ahora).valor());
        Jwt segundo = validador.decode(emisor.emitir(cuenta, ahora).valor());

        assertThat(primero.getId()).isNotEqualTo(segundo.getId());
    }

    /** La caducidad que se devuelve junto al token es la misma que va dentro de él. */
    @Test
    void la_caducidad_devuelta_coincide_con_la_del_token() {
        TokenAcceso token = emisor.emitir(cuenta, ahora);

        assertThat(token.expiraEn()).isEqualTo(validador.decode(token.valor()).getExpiresAt());
    }

    /** Un administrador lleva su propio rol. */
    @Test
    void el_rol_sale_de_la_cuenta() {
        Usuario admin = new Usuario(UUID.randomUUID(), NOMBRE, CORREO, new CorreoNormalizado(CORREO),
                "hash-cualquiera", Rol.ADMIN, EstadoCuenta.ACTIVA);

        Jwt jwt = validador.decode(emisor.emitir(admin, ahora).valor());

        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("ADMIN"));
    }
}