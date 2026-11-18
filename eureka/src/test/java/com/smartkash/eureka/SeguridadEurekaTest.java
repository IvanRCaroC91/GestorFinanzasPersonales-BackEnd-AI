package com.smartkash.eureka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba que el registro de servicios solo acepta a quien presenta las credenciales.
 *
 * <p>Qué se prueba: la cadena de filtros de {@link SeguridadEureka}, con el servidor completo
 * arrancado tal como lo hace Spring Boot. Las peticiones se simulan con MockMvc, que las pasa
 * por los filtros de seguridad reales sin abrir ningún puerto.</p>
 *
 * <p>Una limitación que conviene conocer: Eureka atiende sus rutas {@code /eureka/**} con un
 * filtro web propio que MockMvc no incluye. Por eso, cuando la seguridad deja pasar una
 * petición, la respuesta no es la de Eureka (suele ser un 404). Lo que interesa aquí es solo
 * si la seguridad la detuvo (401 o 403) o la dejó pasar.</p>
 *
 * <p>Las credenciales son inventadas y se pasan como propiedades de la prueba, así que no se
 * depende del archivo {@code .env} ni de variables del sistema.</p>
 */
@SpringBootTest(properties = {
        "EUREKA_USUARIO=" + SeguridadEurekaTest.USUARIO,
        "EUREKA_CONTRASENA=" + SeguridadEurekaTest.CONTRASENA
})
class SeguridadEurekaTest {

    /** Usuario inventado que solo existe en esta prueba. */
    static final String USUARIO = "servicio-de-prueba";

    /** Contraseña inventada que solo existe en esta prueba. */
    static final String CONTRASENA = "contrasena-de-prueba";

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        // springSecurity() añade al simulador la cadena de filtros de seguridad real. Sin él,
        // MockMvc llamaría directamente a los controladores y no se probaría nada.
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    /** Sin credenciales, nadie puede consultar qué servicios hay ni dónde están. */
    @Test
    void la_consulta_sin_credenciales_se_rechaza() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(get("/eureka/apps"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(401);
    }

    /** Una contraseña equivocada se trata igual que no traer ninguna. */
    @Test
    void la_consulta_con_credenciales_erroneas_se_rechaza() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(get("/eureka/apps")
                        .with(httpBasic(USUARIO, "contrasena-equivocada")))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(401);
    }

    /** Con las credenciales correctas, la seguridad deja pasar la consulta. */
    @Test
    void la_consulta_con_credenciales_correctas_pasa_la_seguridad() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(get("/eureka/apps")
                        .with(httpBasic(USUARIO, CONTRASENA)))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isNotIn(401, 403);
    }

    /**
     * Un servicio con credenciales puede registrarse. El registro es un {@code POST}; si la
     * protección CSRF siguiera activa, se rechazaría con 403 porque el servicio no envía el
     * código CSRF que esa protección espera.
     */
    @Test
    void el_registro_con_credenciales_correctas_no_lo_bloquea_csrf() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(post("/eureka/apps/SERVICIO-DE-PRUEBA")
                        .with(httpBasic(USUARIO, CONTRASENA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertThat(respuesta.getStatus()).isNotIn(401, 403);
    }
}
