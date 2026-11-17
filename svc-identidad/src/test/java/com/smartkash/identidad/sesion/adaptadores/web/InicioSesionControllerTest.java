package com.smartkash.identidad.sesion.adaptadores.web;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.sesion.puertos.ConsultaDePoliticas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba la ruta de inicio de sesión de punta a punta: petición HTTP, filtros de seguridad,
 * caso de uso, PostgreSQL real y respuesta.
 *
 * <p>Qué hace: arranca el servicio completo con un PostgreSQL desechable, crea una cuenta con
 * una contraseña conocida y la usa para iniciar sesión. Las peticiones pasan por la cadena de
 * filtros real ({@code springSecurity()}), así que también se comprueba que la ruta es
 * pública.</p>
 *
 * <p>Cada prueba usa su propia cuenta y su propia dirección IP de origen, para que los
 * contadores de fallos de una no afecten a otra.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class InicioSesionControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    private static final String RUTA = "/api/v1/auth/inicio-sesion";
    private static final String CONTRASENA = "una frase larga y dificil 2026";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RepositorioUsuarios usuarios;

    @Autowired
    private HasheadorContrasena hasheador;

    @Autowired
    private JwtDecoder validadorDeTokens;

    @Autowired
    private JdbcTemplate jdbc;

    /* El adaptador real, salvo en la prueba que le pide responder que hay política pendiente. */
    @MockitoSpyBean
    private ConsultaDePoliticas politicas;

    private MockMvc mvc;
    private Usuario cuenta;
    private String ip;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        String correo = "persona." + UUID.randomUUID() + "@Correo.com";
        cuenta = Usuario.nuevo("Ana", correo, new CorreoNormalizado(correo), hasheador.hashear(CONTRASENA));
        usuarios.guardar(cuenta);
        ip = "10.0." + ThreadLocalRandom.current().nextInt(256) + "." + ThreadLocalRandom.current().nextInt(1, 255);
    }

    // ---------- Credenciales correctas ----------

    /**
     * Con credenciales correctas responde 200 con la sesión: el token de acceso firmado por el
     * servicio y los datos de la cuenta. Nunca devuelve el hash ni la contraseña.
     */
    @Test
    void con_credenciales_correctas_devuelve_la_sesion() throws Exception {
        MockHttpServletResponse respuesta = iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA));

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode sesion = JSON.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));

        String token = sesion.get("tokenAcceso").asString();
        assertThat(validadorDeTokens.decode(token).getSubject()).isEqualTo(cuenta.id().toString());
        assertThat(sesion.get("tipoToken").asString()).isEqualTo("Bearer");
        assertThat(Instant.parse(sesion.get("expiraEn").asString())).isAfter(Instant.now());
        assertThat(sesion.get("requiereAceptarPolitica").asBoolean()).isFalse();
        assertThat(sesion.get("eliminacionProgramadaEn").isNull()).isTrue();

        JsonNode usuario = sesion.get("usuario");
        assertThat(usuario.get("id").asString()).isEqualTo(cuenta.id().toString());
        assertThat(usuario.get("nombre").asString()).isEqualTo("Ana");
        assertThat(usuario.get("correo").asString()).isEqualTo(cuenta.correoOriginal());
        assertThat(usuario.get("rol").asString()).isEqualTo("USUARIO");
        assertThat(usuario.get("estado").asString()).isEqualTo("PENDIENTE_VERIFICACION");

        assertThat(nombresDeCampos(sesion)).containsExactlyInAnyOrder("tokenAcceso", "tipoToken",
                "expiraEn", "requiereAceptarPolitica", "eliminacionProgramadaEn", "usuario");
        assertThat(nombresDeCampos(usuario)).containsExactlyInAnyOrder("id", "nombre", "correo", "rol", "estado");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(CONTRASENA).doesNotContain(cuenta.hashContrasena());
    }

    /**
     * El token de renovación viaja solo en la cookie, con todos sus atributos de seguridad, y
     * en la base queda solo su huella.
     */
    @Test
    void entrega_el_token_de_renovacion_en_una_cookie_segura() throws Exception {
        MockHttpServletResponse respuesta = iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA));

        String cookie = respuesta.getHeader("Set-Cookie");
        assertThat(cookie).startsWith("renovacion=")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=604800")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        String valor = cookie.substring("renovacion=".length(), cookie.indexOf(';'));
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).doesNotContain(valor);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tokens_renovacion WHERE hash_token = ?",
                Integer.class, sha256(valor))).isEqualTo(1);
    }

    /** La respuesta con los tokens no se puede guardar en ninguna caché. */
    @Test
    void la_respuesta_no_se_guarda_en_cache() throws Exception {
        MockHttpServletResponse respuesta = iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA));

        assertThat(respuesta.getHeader("Cache-Control")).contains("no-store");
    }

    /** Una cuenta en periodo de gracia entra y recibe la fecha en que se borrará. */
    @Test
    void una_cuenta_en_periodo_de_gracia_recibe_la_fecha_de_borrado() throws Exception {
        Instant fecha = Instant.parse("2026-12-01T00:00:00Z");
        jdbc.update("UPDATE usuarios SET estado = 'PENDIENTE_ELIMINACION', eliminacion_programada_en = ? "
                + "WHERE id = ?", Timestamp.from(fecha), cuenta.id());

        JsonNode sesion = JSON.readTree(iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA))
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(Instant.parse(sesion.get("eliminacionProgramadaEn").asString())).isEqualTo(fecha);
        assertThat(sesion.get("usuario").get("estado").asString()).isEqualTo("PENDIENTE_ELIMINACION");
    }

    /** Si hay una política pendiente, la sesión se inicia igual y la respuesta lo indica. */
    @Test
    void indica_si_hay_una_politica_pendiente() throws Exception {
        doReturn(true).when(politicas).hayPoliticaPendiente(any(), any());

        JsonNode sesion = JSON.readTree(iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA))
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(sesion.get("requiereAceptarPolitica").asBoolean()).isTrue();
    }

    // ---------- Credenciales incorrectas ----------

    /**
     * Contraseña errónea y correo sin cuenta reciben exactamente la misma respuesta: mismo
     * código, mismas cabeceras y mismo cuerpo, sin cookie.
     */
    @Test
    void contrasena_erronea_y_correo_sin_cuenta_responden_igual() throws Exception {
        MockHttpServletResponse porContrasena = iniciar(cuerpo(cuenta.correoOriginal(), "otra frase cualquiera"));
        MockHttpServletResponse porCorreo = iniciar(cuerpo("nadie." + UUID.randomUUID() + "@correo.com", CONTRASENA));

        assertThat(porContrasena.getStatus()).isEqualTo(401);
        assertThat(porContrasena.getHeader("Set-Cookie")).isNull();
        assertThat(porContrasena.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"type\":\"/problemas/no-autenticado\"")
                .contains("\"detail\":\"Correo o contraseña incorrectos.\"");
        assertThat(huellaDeRespuesta(porCorreo)).isEqualTo(huellaDeRespuesta(porContrasena));
    }

    // ---------- Bloqueo ----------

    /**
     * Tras 5 fallos, la contraseña correcta recibe 429 con el tiempo de espera en segundos y el
     * error estándar de demasiadas solicitudes.
     */
    @Test
    void tras_cinco_fallos_responde_429_con_retry_after() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(iniciar(cuerpo(cuenta.correoOriginal(), "otra frase cualquiera")).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse respuesta = iniciar(cuerpo(cuenta.correoOriginal(), CONTRASENA));

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(respuesta.getHeader("Retry-After")).isEqualTo("60");
        assertThat(respuesta.getHeader("Set-Cookie")).isNull();
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"type\":\"/problemas/demasiadas-solicitudes\"")
                .contains("\"status\":429");
    }

    // ---------- Dirección IP ----------

    /**
     * La dirección IP sale de la conexión directa. La cabecera {@code X-Forwarded-For}, que el
     * cliente puede inventar, se ignora: si contara, un atacante cambiaría de IP en cada
     * intento y nunca le afectaría el límite por IP.
     */
    @Test
    void la_ip_sale_de_la_conexion_y_no_de_x_forwarded_for() throws Exception {
        String inventada = "198.51.100." + ThreadLocalRandom.current().nextInt(1, 255);

        iniciar(cuerpo(cuenta.correoOriginal(), "otra frase cualquiera").header("X-Forwarded-For", inventada));

        assertThat(contadorDe("IP:" + ip)).isEqualTo(1);
        assertThat(contadorDe("IP:" + inventada)).isZero();
    }

    // ---------- Forma de la solicitud ----------

    /** Sin contraseña, con un correo mal formado o con un campo de más, responde 400. */
    @Test
    void una_solicitud_mal_formada_responde_400() throws Exception {
        List<String> cuerpos = List.of(
                "{\"correo\":\"" + cuenta.correoOriginal() + "\"}",
                "{\"correo\":\"esto-no-es-un-correo\",\"contrasena\":\"x\"}",
                "{\"correo\":\"" + cuenta.correoOriginal() + "\",\"contrasena\":\"\"}",
                "{\"correo\":\"" + cuenta.correoOriginal() + "\",\"contrasena\":\"x\",\"rol\":\"ADMIN\"}");

        for (String cuerpo : cuerpos) {
            MockHttpServletResponse respuesta = iniciar(post(RUTA)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
            assertThat(respuesta.getStatus()).as(cuerpo).isEqualTo(400);
            assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).contains("/problemas/validacion");
        }
        // Una solicitud mal formada no llega al caso de uso: no suma fallos.
        assertThat(contadorDe("IP:" + ip)).isZero();
    }

    // ---------- Ayudas ----------

    private MockHttpServletResponse iniciar(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.with(solicitud -> {
            solicitud.setRemoteAddr(ip);
            return solicitud;
        })).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder cuerpo(String correo, String contrasena) {
        return post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}");
    }

    private int contadorDe(String texto) {
        return jdbc.queryForObject("SELECT count(*) FROM bloqueos_acceso WHERE clave_hash = ?",
                Integer.class, sha256(texto));
    }

    private static List<String> nombresDeCampos(JsonNode nodo) {
        return List.copyOf(nodo.propertyNames());
    }

    /* Código, cabeceras y cuerpo de una respuesta, para comparar respuestas enteras. */
    private static String huellaDeRespuesta(MockHttpServletResponse respuesta) throws Exception {
        Map<String, List<String>> cabeceras = new TreeMap<>();
        for (String nombre : respuesta.getHeaderNames()) {
            cabeceras.put(nombre, respuesta.getHeaders(nombre));
        }
        return respuesta.getStatus() + "\n" + cabeceras + "\n"
                + respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
