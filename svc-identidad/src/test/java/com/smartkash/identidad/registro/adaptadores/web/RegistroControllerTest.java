package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.PruebaConContenedores;
import com.smartkash.identidad.registro.aplicacion.DatosDeRegistro;
import com.smartkash.identidad.registro.aplicacion.RegistrarUsuario;
import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba el registro de punta a punta: desde la petición web hasta la base de datos.
 *
 * <p>Qué hace: levanta el servicio completo contra un PostgreSQL real y desechable, y le
 * envía peticiones simuladas a la ruta de registro. Las peticiones no viajan por la red:
 * {@code MockMvc} las entrega directamente al controlador, pasando por la misma conversión
 * de JSON que una petición real.</p>
 *
 * <p>De dónde vienen los datos: cada prueba usa un correo al azar. A dónde van: a una base de
 * datos que se destruye al terminar.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class RegistroControllerTest extends PruebaConContenedores {

    private static final String RUTA = "/api/v1/auth/registro";
    private static final String CONTRASENA = "una frase larga y dificil 2026";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RegistrarUsuario registrarUsuario;

    /*
     * Un "espía": es el repositorio real, pero una prueba puede pedirle que falle para simular
     * una avería. En las demás pruebas se comporta con normalidad.
     */
    @MockitoSpyBean
    private RepositorioAutorizaciones autorizaciones;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    /**
     * Un registro correcto responde 202 con un mensaje, y deja guardada la cuenta (con el rol
     * y el estado que decide el servidor y la contraseña solo como hash) y la prueba de que
     * la persona aceptó la política.
     */
    @Test
    void registra_una_cuenta_nueva() throws Exception {
        String correo = correoAlAzar();

        MockHttpServletResponse respuesta = registrar(cuerpo("Ana", correo));

        assertThat(respuesta.getStatus()).isEqualTo(202);
        assertThat(respuesta.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).startsWith("{\"mensaje\":\"");

        Map<String, Object> cuenta =
                jdbc.queryForMap("SELECT * FROM usuarios WHERE email_normalizado = ?", correo);
        assertThat(cuenta.get("nombre")).isEqualTo("Ana");
        assertThat(cuenta.get("rol")).isEqualTo("USUARIO");
        assertThat(cuenta.get("estado")).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat((String) cuenta.get("hash_contrasena")).startsWith("$argon2id$");

        Map<String, Object> autorizacion = jdbc.queryForMap(
                "SELECT * FROM autorizaciones WHERE usuario_id = ?", cuenta.get("id"));
        assertThat(autorizacion.get("politica_version")).isEqualTo("1.0");
        assertThat(autorizacion.get("otorgada")).isEqualTo(true);
    }

    /**
     * Registrarse con un correo que ya existe, aunque se escriba con otras mayúsculas, recibe
     * exactamente la misma respuesta que un registro correcto: mismo código, mismo tipo de
     * contenido y mismo cuerpo. Así nadie puede averiguar qué correos tienen cuenta. Tampoco
     * se crea una segunda cuenta.
     */
    @Test
    void el_correo_nuevo_y_el_repetido_reciben_la_misma_respuesta() throws Exception {
        String correo = correoAlAzar();

        MockHttpServletResponse conCorreoNuevo = registrar(cuerpo("Ana", correo));
        MockHttpServletResponse conCorreoRepetido = registrar(cuerpo("Otra", correo.toUpperCase()));

        assertThat(conCorreoRepetido.getStatus()).isEqualTo(conCorreoNuevo.getStatus()).isEqualTo(202);
        assertThat(conCorreoRepetido.getContentType()).isEqualTo(conCorreoNuevo.getContentType());
        assertThat(conCorreoRepetido.getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(conCorreoNuevo.getContentAsString(StandardCharsets.UTF_8));
        assertThat(conCorreoRepetido.getHeaderNames())
                .containsExactlyInAnyOrderElementsOf(conCorreoNuevo.getHeaderNames());

        assertThat(cuentasCon(correo)).isEqualTo(1);
    }

    /** La respuesta nunca devuelve la contraseña ni el correo que se enviaron. */
    @Test
    void la_respuesta_no_devuelve_la_contrasena_ni_el_correo() throws Exception {
        String correo = correoAlAzar();

        String respuesta = registrar(cuerpo("Ana", correo)).getContentAsString(StandardCharsets.UTF_8);

        assertThat(respuesta).doesNotContain(CONTRASENA).doesNotContain(correo);
    }

    /**
     * Una solicitud con campos que no existen en el contrato, como el rol, se rechaza con un
     * error 400 y no crea ninguna cuenta. El rol, el estado y el identificador los decide
     * siempre el servidor.
     */
    @Test
    void rechaza_una_solicitud_con_campos_desconocidos() throws Exception {
        String correo = correoAlAzar();
        String conRol = cuerpo("Ana", correo).replace("{", "{\"rol\":\"ADMIN\",");

        MockHttpServletResponse respuesta = registrar(conRol);

        assertThat(respuesta.getStatus()).isEqualTo(400);
        assertThat(cuentasCon(correo)).isZero();
    }

    /**
     * La cuenta y su prueba de autorización se guardan juntas o no se guarda ninguna: si
     * falla el guardado de la autorización, la cuenta tampoco queda. Aquí se llama al caso de
     * uso directamente, que es quien abre la transacción.
     */
    @Test
    void si_falla_la_autorizacion_no_queda_la_cuenta() {
        String correo = correoAlAzar();
        // Se simula el tipo de error que da Spring cuando la base de datos deja de responder.
        doThrow(new DataAccessResourceFailureException("Fallo simulado"))
                .when(autorizaciones).guardar(any(Autorizacion.class));

        assertThatThrownBy(() -> registrarUsuario.ejecutar(
                new DatosDeRegistro("Ana", correo, CONTRASENA, true, "1.0")))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(cuentasCon(correo)).isZero();
    }

    private MockHttpServletResponse registrar(String cuerpoJson) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoJson))
                .andReturn().getResponse();
    }

    private static String cuerpo(String nombre, String correo) {
        return """
                {"nombre":"%s","correo":"%s","contrasena":"%s","aceptaPolitica":true,"versionPolitica":"1.0"}
                """.formatted(nombre, correo, CONTRASENA);
    }

    private static String correoAlAzar() {
        return "persona." + UUID.randomUUID() + "@correo.com";
    }

    private Integer cuentasCon(String correo) {
        return jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email_normalizado = ?",
                Integer.class, correo.toLowerCase());
    }
}
