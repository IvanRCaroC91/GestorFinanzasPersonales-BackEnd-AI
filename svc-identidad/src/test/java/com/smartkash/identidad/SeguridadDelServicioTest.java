package com.smartkash.identidad;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba la seguridad con el servicio completo, tal como arranca de verdad.
 *
 * <p>Por qué existe además de la prueba de la configuración de seguridad: aquí Spring Boot
 * aplica toda su configuración automática. Así se comprueba que la cadena de filtros del
 * servicio es la que manda (y no la que Spring Boot crea por defecto, que lo cerraría todo) y
 * que el registro real sigue siendo público.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class SeguridadDelServicioTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private EmisorTokenAcceso emisor;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    /**
     * El registro llega a su controlador sin token: con un cuerpo vacío responde 400 por los
     * datos, no 401 por falta de autenticación.
     */
    @Test
    void el_registro_sigue_siendo_publico() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(400);
    }

    /**
     * Las claves públicas se publican sin token, en formato JWKS, y sin la parte privada.
     */
    @Test
    void publica_las_claves_publicas_sin_la_parte_privada() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(get("/.well-known/jwks.json"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        RSAKey publicada = (RSAKey) JWKSet.parse(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .getKeys().getFirst();
        assertThat(publicada.isPrivate()).isFalse();
        assertThat(publicada.getKeyID()).isNotBlank();
    }

    /**
     * Con la clave publicada se puede comprobar un token emitido por el servicio. Es lo que
     * harán los demás servicios: descargar las claves y validar los tokens sin llamar a este.
     */
    @Test
    void la_clave_publicada_comprueba_los_tokens_del_servicio() throws Exception {
        String json = mvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        RSAKey publicada = (RSAKey) JWKSet.parse(json).getKeys().getFirst();
        Usuario cuenta = new Usuario(UUID.randomUUID(), "Ana", "ana@correo.com",
                new CorreoNormalizado("ana@correo.com"), "hash-cualquiera", Rol.USUARIO, EstadoCuenta.ACTIVA);
        String token = emisor.emitir(cuenta, Instant.now().truncatedTo(ChronoUnit.SECONDS)).valor();

        Jwt comprobado = NimbusJwtDecoder.withPublicKey(publicada.toRSAPublicKey()).build().decode(token);

        assertThat(comprobado.getSubject()).isEqualTo(cuenta.id().toString());
        assertThat(comprobado.getHeaders()).containsEntry("kid", publicada.getKeyID());
    }

    /** Cualquier otra ruta exige token y responde con el 401 estándar del servicio. */
    @Test
    void cualquier_otra_ruta_exige_token() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(get("/api/v1/cualquier-cosa"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("/problemas/no-autenticado");
    }
}
