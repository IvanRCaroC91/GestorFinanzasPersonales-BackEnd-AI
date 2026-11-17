package com.smartkash.identidad;

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

import java.nio.charset.StandardCharsets;

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
