package com.smartkash.finanzas.cuentas.adaptadores.web;

import com.smartkash.finanzas.PruebaConContenedores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Comprueba la ruta que lista las cuentas: seguridad, dueño de los datos y forma del JSON.
 *
 * <p>Qué hace: arranca el servicio completo contra el PostgreSQL desechable que comparten las
 * pruebas, siembra cuentas con SQL y las pide por HTTP con MockMvc. Las peticiones pasan por la
 * cadena de filtros real ({@code springSecurity()}), así que se comprueba también qué ocurre
 * sin token.</p>
 *
 * <p>Qué es MockMvc: un cliente HTTP de pruebas que entrega la petición a Spring sin abrir un
 * puerto de red. Recorre los mismos filtros, controladores y manejadores de errores que una
 * petición real.</p>
 *
 * <p>Cómo se simula el token: {@code jwt()} de Spring Security Test entrega a los filtros un
 * token ya validado con el {@code sub} que se le indique, sin firmarlo ni descargar claves. Así
 * esta prueba se centra en la ruta; la validación real de la firma se prueba en
 * {@code ValidadorDeTokensTest} y en {@code RecorridoConTokenRealTest}.</p>
 *
 * <p>Independencia: cada prueba usa personas al azar, así que no le afectan las cuentas que
 * dejen otras pruebas en la base compartida.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class CuentasControllerTest extends PruebaConContenedores {

    private static final String RUTA = "/api/v1/cuentas";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    // ---------- Sin un token válido ----------

    /**
     * Sin token responde {@code 401} con el error estándar del servicio (RFC 9457): tipo,
     * título y explicación genérica. La cabecera {@code WWW-Authenticate} dice solo
     * {@code Bearer}, sin el motivo del rechazo, y no se crea sesión en el servidor.
     */
    @Test
    void sin_token_responde_401_con_el_error_estandar() throws Exception {
        MvcResult resultado = mvc.perform(get(RUTA)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        JsonNode error = cuerpo(resultado);
        assertThat(error.get("type").asString()).isEqualTo("/problemas/no-autenticado");
        assertThat(error.get("title").asString()).isEqualTo("Se requiere autenticación");
        assertThat(error.get("status").asInt()).isEqualTo(401);
        assertThat(error.get("detail").asString()).isEqualTo("Se requiere un token de acceso válido.");

        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    /**
     * Un token ilegible recibe exactamente la misma respuesta que la falta de token: quien
     * prueba tokens no aprende nada del motivo del rechazo.
     */
    @Test
    void un_token_ilegible_recibe_la_misma_respuesta_que_la_falta_de_token() throws Exception {
        MvcResult sinToken = mvc.perform(get(RUTA)).andReturn();
        MvcResult ilegible = mvc.perform(get(RUTA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-token")).andReturn();

        assertThat(ilegible.getResponse().getStatus()).isEqualTo(401);
        assertThat(ilegible.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(cuerpo(ilegible)).isEqualTo(cuerpo(sinToken));
    }

    // ---------- Con token ----------

    /** Una persona sin cuentas recibe {@code 200} con una lista vacía, no un error. */
    @Test
    void sin_cuentas_devuelve_una_lista_vacia() throws Exception {
        MvcResult resultado = mvc.perform(get(RUTA).with(tokenDe(UUID.randomUUID()))).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo(resultado).isArray()).isTrue();
        assertThat(cuerpo(resultado).isEmpty()).isTrue();
    }

    /**
     * Devuelve solo las cuentas del dueño del token, ordenadas por nombre e incluidas las
     * archivadas. Las de otra persona no aparecen.
     */
    @Test
    void devuelve_solo_las_cuentas_del_dueno_del_token_ordenadas_por_nombre() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID otra = UUID.randomUUID();
        sembrar(ana, "Nomina", false);
        sembrar(ana, "Ahorro", true);
        sembrar(otra, "Cuenta ajena", false);

        MvcResult resultado = mvc.perform(get(RUTA).with(tokenDe(ana))).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(nombres(cuerpo(resultado))).containsExactly("Ahorro", "Nomina");
    }

    /**
     * El dueño sale siempre del token. Si la petición trae el identificador de otra persona en
     * la dirección, se ignora: nadie puede pedir las cuentas de otro cambiando la URL.
     */
    @Test
    void ignora_el_identificador_de_otra_persona_enviado_en_la_peticion() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID victima = UUID.randomUUID();
        sembrar(ana, "De Ana", false);
        sembrar(victima, "De la victima", false);

        MvcResult resultado = mvc.perform(get(RUTA).with(tokenDe(ana))
                .param("usuario", victima.toString())
                .param("usuarioRef", victima.toString())
                .param("sub", victima.toString())).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(nombres(cuerpo(resultado))).containsExactly("De Ana");
    }

    /**
     * El JSON de cada cuenta tiene exactamente los campos del contrato, con su formato: el
     * saldo como número con sus céntimos y su signo, la fecha como {@code AAAA-MM-DD} y el tipo
     * con su nombre. No incluye el dueño ni datos internos como la versión.
     */
    @Test
    void cada_cuenta_tiene_exactamente_los_campos_del_contrato() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, saldo_inicial, fecha_inicio,
                                     predeterminada, archivada)
                VALUES (?, ?, 'BILLETERA', 'Monedero digital', -1250.50, ?, true, false)
                """,
                id, ana, LocalDate.of(2025, 12, 31));

        MvcResult resultado = mvc.perform(get(RUTA).with(tokenDe(ana))).andReturn();

        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode cuenta = cuerpo(resultado).get(0);
        assertThat(nombresDeCampos(cuenta)).containsExactlyInAnyOrder(
                "id", "tipo", "nombre", "saldoInicial", "fechaInicio", "predeterminada", "archivada");
        assertThat(cuenta.get("id").asString()).isEqualTo(id.toString());
        assertThat(cuenta.get("tipo").asString()).isEqualTo("BILLETERA");
        assertThat(cuenta.get("nombre").asString()).isEqualTo("Monedero digital");
        assertThat(cuenta.get("saldoInicial").isNumber()).isTrue();
        assertThat(cuenta.get("saldoInicial").decimalValue()).isEqualByComparingTo(new BigDecimal("-1250.50"));
        assertThat(cuenta.get("fechaInicio").asString()).isEqualTo("2025-12-31");
        assertThat(cuenta.get("predeterminada").asBoolean()).isTrue();
        assertThat(cuenta.get("archivada").asBoolean()).isFalse();
    }

    // ---------- Ayudas ----------

    /* Simula un token ya validado cuyo dato sub es la persona indicada. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor tokenDe(UUID usuario) {
        return jwt().jwt(token -> token.subject(usuario.toString()));
    }

    private void sembrar(UUID usuario, String nombre, boolean archivada) {
        jdbc.update(
                """
                INSERT INTO cuentas (id, usuario_ref, tipo, nombre, saldo_inicial, fecha_inicio, archivada)
                VALUES (?, ?, 'AHORROS', ?, 0, ?, ?)
                """,
                UUID.randomUUID(), usuario, nombre, LocalDate.of(2026, 1, 1), archivada);
    }

    private static JsonNode cuerpo(MvcResult resultado) throws Exception {
        return JSON.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static List<String> nombres(JsonNode cuentas) {
        List<String> nombres = new ArrayList<>();
        cuentas.forEach(cuenta -> nombres.add(cuenta.get("nombre").asString()));
        return nombres;
    }

    private static List<String> nombresDeCampos(JsonNode nodo) {
        List<String> campos = new ArrayList<>();
        nodo.propertyNames().forEach(campos::add);
        return campos;
    }
}
