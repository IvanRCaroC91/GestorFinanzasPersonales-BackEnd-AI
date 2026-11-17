package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba las respuestas de error del registro.
 *
 * <p>Qué hace: envía solicitudes inválidas a la ruta de registro y revisa que la respuesta
 * siga el formato estándar de errores RFC 9457 (una forma pública y común de describir los
 * errores de una API web): tipo de contenido {@code application/problem+json} y un cuerpo con
 * {@code type}, {@code title}, {@code status}, {@code detail} y, en los errores de datos, la
 * lista {@code errores} con el campo y el mensaje de cada problema.</p>
 *
 * <p>De dónde vienen los datos: de cuerpos JSON escritos en cada prueba. A dónde van: a un
 * servicio completo con un PostgreSQL desechable. Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class RegistroErroresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    private static final String RUTA = "/api/v1/auth/registro";
    private static final String CONTRASENA_VALIDA = "una frase larga y dificil 2026";
    private static final String TIPO_VALIDACION = "/problemas/validacion";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    /* El repositorio real, al que una prueba le pide fallar para simular una avería. */
    @MockitoSpyBean
    private RepositorioAutorizaciones autorizaciones;

    /* El calculador de hashes real, vigilado para saber si llegó a usarse. */
    @MockitoSpyBean
    private HasheadorContrasena hasheador;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    /**
     * Una contraseña de 11 caracteres se rechaza con un error que explica la regla y señala
     * el campo. No se crea la cuenta y la respuesta no repite la contraseña.
     */
    @Test
    void contrasena_debil_responde_400_y_explica_la_regla() throws Exception {
        String correo = correoAlAzar();

        ResultActions respuesta = registrar(cuerpo("Ana", correo, "solo11letra", true, "1.0"));

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_MUY_CORTA"))
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("12")));
        assertThat(cuerpoDe(respuesta)).doesNotContain("solo11letra");
        assertThat(cuentasCon(correo)).isZero();
    }

    /** Una contraseña que está en la lista de contraseñas comunes se rechaza. */
    @Test
    void contrasena_comun_responde_400() throws Exception {
        String correo = correoAlAzar();

        ResultActions respuesta = registrar(cuerpo("Ana", correo, "qwerty123456", true, "1.0"));

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.codigo").value("CONTRASENA_COMUN"))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));
        assertThat(cuentasCon(correo)).isZero();
    }

    /**
     * Si no se acepta la política, el registro se rechaza indicando que la aceptación es
     * obligatoria, y no se crea la cuenta.
     */
    @Test
    void politica_sin_aceptar_responde_400() throws Exception {
        String correo = correoAlAzar();

        ResultActions respuesta = registrar(cuerpo("Ana", correo, CONTRASENA_VALIDA, false, "1.0"));

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.codigo").value("POLITICA_NO_ACEPTADA"))
                .andExpect(jsonPath("$.errores[0].campo").value("aceptaPolitica"))
                .andExpect(jsonPath("$.errores[0].mensaje", containsString("aceptar")));
        assertThat(cuentasCon(correo)).isZero();
    }

    /** Aceptar una versión de la política que no es la vigente se rechaza señalando ese campo. */
    @Test
    void version_de_politica_no_vigente_responde_400() throws Exception {
        ResultActions respuesta =
                registrar(cuerpo("Ana", correoAlAzar(), CONTRASENA_VALIDA, true, "0.9"));

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.codigo").value("VERSION_POLITICA_NO_VIGENTE"))
                .andExpect(jsonPath("$.errores[0].campo").value("versionPolitica"));
    }

    /** Si faltan campos obligatorios, la respuesta los lista todos de una vez. */
    @Test
    void campos_faltantes_se_listan_todos() throws Exception {
        ResultActions respuesta = registrar("{}");

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder(
                        "nombre", "correo", "contrasena", "aceptaPolitica", "versionPolitica")));
    }

    /** Un correo que no tiene forma de correo se rechaza señalando ese campo. */
    @Test
    void correo_con_formato_invalido_responde_400() throws Exception {
        ResultActions respuesta =
                registrar(cuerpo("Ana", "esto-no-es-un-correo", CONTRASENA_VALIDA, true, "1.0"));

        esErrorDeValidacion(respuesta)
                .andExpect(jsonPath("$.errores", hasSize(1)))
                .andExpect(jsonPath("$.errores[0].campo").value("correo"));
    }

    /**
     * Un cuerpo que no es JSON válido responde con el mismo formato de error, sin nombres de
     * clases ni otros detalles internos.
     */
    @Test
    void json_mal_formado_responde_400_sin_detalles_internos() throws Exception {
        ResultActions respuesta = registrar("{\"nombre\": ");

        esErrorDeValidacion(respuesta);
        assertThat(cuerpoDe(respuesta)).doesNotContain("jackson").doesNotContain("smartkash");
    }

    /** Un campo que no está en el contrato responde con el mismo formato de error. */
    @Test
    void campo_desconocido_responde_400_en_formato_estandar() throws Exception {
        String conRol = cuerpo("Ana", correoAlAzar(), CONTRASENA_VALIDA, true, "1.0")
                .replace("{", "{\"rol\":\"ADMIN\",");

        ResultActions respuesta = registrar(conRol);

        esErrorDeValidacion(respuesta);
        assertThat(cuerpoDe(respuesta)).doesNotContain("smartkash");
    }

    /**
     * Los errores de datos son iguales tenga o no cuenta el correo: una contraseña débil
     * recibe exactamente la misma respuesta con un correo nuevo que con uno ya registrado.
     */
    @Test
    void el_error_es_igual_con_correo_nuevo_y_con_correo_registrado() throws Exception {
        String registrado = correoAlAzar();
        registrar(cuerpo("Ana", registrado, CONTRASENA_VALIDA, true, "1.0")).andExpect(status().isAccepted());

        ResultActions conRegistrado = registrar(cuerpo("Ana", registrado, "solo11letra", true, "1.0"));
        ResultActions conNuevo = registrar(cuerpo("Ana", correoAlAzar(), "solo11letra", true, "1.0"));

        conRegistrado.andExpect(status().isBadRequest());
        assertThat(cuerpoDe(conRegistrado)).isEqualTo(cuerpoDe(conNuevo));
    }

    /**
     * Un fallo inesperado responde 500 con el formato estándar y sin dar detalles: el motivo
     * real queda en el log del servidor, nunca en la respuesta.
     */
    @Test
    void un_fallo_inesperado_responde_500_sin_detalles() throws Exception {
        doThrow(new DataAccessResourceFailureException("detalle interno que no debe salir"))
                .when(autorizaciones).guardar(any(Autorizacion.class));

        ResultActions respuesta =
                registrar(cuerpo("Ana", correoAlAzar(), CONTRASENA_VALIDA, true, "1.0"));

        respuesta.andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problemas/interno"))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.status").value(500));
        assertThat(cuerpoDe(respuesta)).doesNotContain("detalle interno");
    }

    /**
     * Un nombre o una contraseña de tamaño desmesurado se rechazan en la puerta, antes de
     * calcular ningún hash. El hash es caro a propósito (memoria y tiempo), así que no debe
     * gastarse en solicitudes que de todos modos se van a rechazar.
     */
    @Test
    void valores_desmesurados_se_rechazan_sin_calcular_el_hash() throws Exception {
        String nombreEnorme = "a".repeat(5_000);
        String contrasenaEnorme = "b".repeat(5_000);

        esErrorDeValidacion(registrar(
                cuerpo(nombreEnorme, correoAlAzar(), CONTRASENA_VALIDA, true, "1.0")))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        esErrorDeValidacion(registrar(
                cuerpo("Ana", correoAlAzar(), contrasenaEnorme, true, "1.0")))
                .andExpect(jsonPath("$.errores[0].campo").value("contrasena"));

        verify(hasheador, never()).hashear(anyString());
    }

    /**
     * Ni la contraseña ni el correo aparecen en el log del servidor, se registre bien la
     * cuenta, se rechace la solicitud o falle algo por dentro. {@code CapturedOutput} recoge
     * todo lo que el servicio escribe en la consola durante la prueba.
     */
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void el_log_no_contiene_contrasenas_ni_correos(CapturedOutput log) throws Exception {
        String marcaCorreo = "buscame." + UUID.randomUUID();
        String correo = marcaCorreo + "@correo.com";
        String contrasenaDebil = "debil-" + UUID.randomUUID().toString().substring(0, 5);

        registrar(cuerpo("Ana", correo, CONTRASENA_VALIDA, true, "1.0"));
        registrar(cuerpo("Ana", correo, CONTRASENA_VALIDA, true, "1.0"));
        registrar(cuerpo("Ana", correo, contrasenaDebil, true, "1.0"));
        registrar(cuerpo("Ana", correo, CONTRASENA_VALIDA, true, "1.0").replace("{", "{\"rol\":\"ADMIN\","));
        registrar("{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA_VALIDA + "\", ");
        doThrow(new DataAccessResourceFailureException("avería simulada"))
                .when(autorizaciones).guardar(any(Autorizacion.class));
        registrar(cuerpo("Ana", "otra." + correo, CONTRASENA_VALIDA, true, "1.0"));

        assertThat(log.getAll())
                .contains("Error no controlado")
                .doesNotContain(CONTRASENA_VALIDA)
                .doesNotContain(contrasenaDebil)
                .doesNotContain(marcaCorreo);
    }

    /* Comprueba lo que comparten todos los errores de datos de entrada. */
    private static ResultActions esErrorDeValidacion(ResultActions respuesta) throws Exception {
        return respuesta.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(TIPO_VALIDACION))
                .andExpect(jsonPath("$.title").value("Datos de entrada inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(RUTA));
    }

    private ResultActions registrar(String cuerpoJson) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoJson));
    }

    private static String cuerpoDe(ResultActions respuesta) throws Exception {
        return respuesta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String cuerpo(
            String nombre, String correo, String contrasena, boolean acepta, String version) {
        return """
                {"nombre":"%s","correo":"%s","contrasena":"%s","aceptaPolitica":%s,"versionPolitica":"%s"}
                """.formatted(nombre, correo, contrasena, acepta, version);
    }

    private static String correoAlAzar() {
        return "persona." + UUID.randomUUID() + "@correo.com";
    }

    private Integer cuentasCon(String correo) {
        return jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email_normalizado = ?",
                Integer.class, correo.toLowerCase());
    }
}
