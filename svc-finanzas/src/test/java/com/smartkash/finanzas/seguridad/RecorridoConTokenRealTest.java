package com.smartkash.finanzas.seguridad;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.smartkash.finanzas.PruebaConContenedores;
import com.smartkash.finanzas.TokensDePrueba;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.smartkash.finanzas.TokensDePrueba.datosValidos;
import static com.smartkash.finanzas.TokensDePrueba.firmar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recorre la seguridad de punta a punta, como en producción: servidor HTTP real, token firmado
 * de verdad y claves públicas descargadas por la red.
 *
 * <p>Qué hace: levanta un pequeño servidor de claves con el {@code HttpServer} que trae el JDK
 * (sin librerías extra) y publica en él la clave pública, en formato JWKS, igual que lo hace el
 * servicio de identidad. Arranca el servicio en un puerto al azar apuntando a ese servidor y le
 * envía peticiones HTTP de verdad con tokens firmados con la clave privada.</p>
 *
 * <p>Qué es JWKS: un documento JSON con las claves públicas de quien firma los tokens. Cada
 * clave lleva un identificador ({@code kid}) que también va en la cabecera del token, para
 * saber con cuál comprobarlo.</p>
 *
 * <p>Qué es {@code @DynamicPropertySource}: permite dar una propiedad cuyo valor solo se conoce
 * al ejecutar, aquí la dirección del servidor de claves, cuyo puerto elige el sistema.</p>
 *
 * <p>Sonda de permisos: para comprobar cómo se convierten los roles del token, la prueba
 * añade solo en su propio arranque una ruta que devuelve los permisos de quien llama. Es una
 * clase anidada en la prueba, así que el resto de pruebas y el servicio real no la ven.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RecorridoConTokenRealTest.SondaDePermisos.class)
class RecorridoConTokenRealTest extends PruebaConContenedores {

    private static final RSAKey CLAVE = TokensDePrueba.claveNueva();
    private static final AtomicInteger DESCARGAS = new AtomicInteger();
    private static final HttpServer SERVIDOR_DE_CLAVES = servidorDeClaves();

    private final HttpClient cliente = HttpClient.newHttpClient();

    @LocalServerPort
    private int puerto;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void direccionDeLasClaves(DynamicPropertyRegistry registro) {
        registro.add("finanzas.seguridad.jwks-uri", () -> "http://localhost:"
                + SERVIDOR_DE_CLAVES.getAddress().getPort() + "/.well-known/jwks.json");
    }

    @AfterAll
    static void apagarServidorDeClaves() {
        SERVIDOR_DE_CLAVES.stop(0);
    }

    /**
     * Con un token firmado por la clave publicada, el servicio descarga las claves, lo acepta y
     * devuelve las cuentas de su dueño.
     */
    @Test
    void con_un_token_firmado_por_identidad_devuelve_las_cuentas_del_dueno() throws Exception {
        UUID ana = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, fecha_inicio)
                VALUES (?, ?, 'EFECTIVO', 'Cartera', ?)
                """, UUID.randomUUID(), ana, LocalDate.of(2026, 1, 1));

        HttpResponse<String> respuesta = pedir("/api/v1/cuentas",
                firmar(CLAVE, datosValidos(ana, Instant.now()).build()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).contains("\"nombre\":\"Cartera\"");
        assertThat(DESCARGAS.get()).isPositive();
    }

    /**
     * Los roles del token se convierten en permisos con el prefijo {@code ROLE_}, y son los
     * únicos que salen del token: no aparecen permisos de otros datos (por ejemplo los
     * {@code SCOPE_} que Spring crea por defecto).
     *
     * <p>Por qué la sonda descarta los que empiezan por {@code FACTOR_}: Spring Security 7
     * añade por su cuenta un permiso que dice cómo se autenticó la persona (aquí,
     * {@code FACTOR_BEARER}: con un token). No sale del token ni lo configura el servicio.</p>
     */
    @Test
    void los_roles_del_token_se_convierten_en_permisos() throws Exception {
        HttpResponse<String> respuesta = pedir("/api/v1/pruebas/permisos",
                firmar(CLAVE, datosValidos(UUID.randomUUID(), Instant.now()).build()));

        assertThat(respuesta.statusCode()).isEqualTo(200);
        assertThat(respuesta.body()).isEqualTo("[\"ROLE_USUARIO\"]");
    }

    /**
     * Un token firmado con la clave buena pero con otro algoritmo (RS512) se rechaza: el
     * servicio solo acepta RS256, el que usa identidad. La respuesta es el {@code 401}
     * estándar.
     */
    @Test
    void rechaza_un_token_firmado_con_otro_algoritmo() throws Exception {
        String token = firmar(CLAVE, JWSAlgorithm.RS512, datosValidos(UUID.randomUUID(), Instant.now()).build());

        HttpResponse<String> respuesta = pedir("/api/v1/cuentas", token);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
        assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/problem+json"));
        assertThat(respuesta.body()).contains("/problemas/no-autenticado");
    }

    /** Un token firmado con una clave que identidad no publica se rechaza. */
    @Test
    void rechaza_un_token_firmado_con_una_clave_no_publicada() throws Exception {
        String token = firmar(TokensDePrueba.claveNueva(), datosValidos(UUID.randomUUID(), Instant.now()).build());

        HttpResponse<String> respuesta = pedir("/api/v1/cuentas", token);

        assertThat(respuesta.statusCode()).isEqualTo(401);
        assertThat(respuesta.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
    }

    // ---------- Ayudas ----------

    private HttpResponse<String> pedir(String ruta, String token) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /*
     * Publica solo la parte pública de la clave, como identidad, y cuenta cuántas veces se
     * descarga. El puerto 0 le pide al sistema uno libre.
     */
    private static HttpServer servidorDeClaves() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] jwks = new JWKSet(CLAVE.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            servidor.createContext("/.well-known/jwks.json", intercambio -> {
                DESCARGAS.incrementAndGet();
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, jwks.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(jwks);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ruta solo de esta prueba: devuelve los permisos que Spring Security dio a quien llama. */
    @RestController
    static class SondaDePermisos {

        /**
         * @param autenticacion quien llama, ya validado por los filtros
         * @return los nombres de sus permisos, sin los que indican cómo se autenticó
         */
        @GetMapping("/api/v1/pruebas/permisos")
        List<String> permisos(Authentication autenticacion) {
            return autenticacion.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(permiso -> !permiso.startsWith("FACTOR_"))
                    .toList();
        }
    }
}
