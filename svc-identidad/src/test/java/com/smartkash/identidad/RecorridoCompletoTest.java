package com.smartkash.identidad;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Recorre de punta a punta lo que hace una persona nueva: registrarse, recibir el correo,
 * verificar con su enlace, iniciar sesión y pedir otro enlace.
 *
 * <p>Qué tiene de especial: no hay ningún doble. Arranca el servicio completo con un
 * PostgreSQL y un Mailpit (servidor de correo de pruebas) reales en Docker. El enlace de
 * verificación no se fabrica en la prueba: se lee del correo que llegó a Mailpit, igual que lo
 * leería la persona. Todas las peticiones pasan por la cadena de filtros de seguridad.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class RecorridoCompletoTest extends PruebaConContenedores {

    private static final String CONTRASENA = "una frase larga y dificil 2026";
    private static final Pattern TOKEN_EN_EL_ENLACE = Pattern.compile("/verificar-correo#token=([A-Za-z0-9_-]+)");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;
    private String correo;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        correo = "persona." + UUID.randomUUID() + "@correo.com";
    }

    /**
     * El recorrido principal, paso a paso. Cada paso comprueba lo que vería la persona.
     */
    @Test
    void registrarse_verificar_iniciar_sesion_y_pedir_otro_enlace() throws Exception {
        // 1. Se registra: respuesta genérica.
        assertThat(registrarse().getStatus()).isEqualTo(202);

        // 2. Le llega un correo con el enlace.
        List<String> enlaces = esperarCorreos(1);
        String token = tokenDelEnlace(enlaces.getFirst());

        // 3. Antes de verificar ya puede iniciar sesión; su cuenta figura como pendiente.
        JsonNode pendiente = cuerpo(iniciarSesion());
        assertThat(pendiente.get("usuario").get("estado").asString()).isEqualTo("PENDIENTE_VERIFICACION");

        // 4. Verifica con el enlace del correo.
        assertThat(verificar(token).getStatus()).isEqualTo(204);

        // 5. El mismo enlace ya no vale.
        MockHttpServletResponse otraVez = verificar(token);
        assertThat(otraVez.getStatus()).isEqualTo(400);
        assertThat(otraVez.getContentAsString(StandardCharsets.UTF_8)).contains("ENLACE_NO_VALIDO");

        // 6. Inicia sesión: recibe su token de acceso, la cookie de renovación y la cuenta activa.
        MockHttpServletResponse sesion = iniciarSesion();
        assertThat(sesion.getStatus()).isEqualTo(200);
        assertThat(sesion.getHeader("Set-Cookie")).startsWith("renovacion=");
        JsonNode activa = cuerpo(sesion);
        assertThat(activa.get("usuario").get("estado").asString()).isEqualTo("ACTIVA");

        // 7. Pide otro enlace con su token: la respuesta es la de siempre, pero como ya está
        //    verificada no le llega ningún correo nuevo.
        MockHttpServletResponse reenvio = reenviar(activa.get("tokenAcceso").asString());
        assertThat(reenvio.getStatus()).isEqualTo(202);
        Thread.sleep(1_500);
        assertThat(enlacesRecibidos()).hasSize(1);
    }

    /**
     * Con la cuenta pendiente, pedir otro enlace envía un segundo correo. El primer enlace deja
     * de valer y el segundo funciona.
     */
    @Test
    void el_reenvio_invalida_el_enlace_anterior() throws Exception {
        registrarse();
        String primero = tokenDelEnlace(esperarCorreos(1).getFirst());
        String tokenDeAcceso = cuerpo(iniciarSesion()).get("tokenAcceso").asString();

        assertThat(reenviar(tokenDeAcceso).getStatus()).isEqualTo(202);
        List<String> enlaces = esperarCorreos(2);
        String segundo = enlaces.stream().map(RecorridoCompletoTest::tokenDelEnlace)
                .filter(valor -> !valor.equals(primero)).findFirst().orElseThrow();

        assertThat(verificar(primero).getStatus()).isEqualTo(400);
        assertThat(verificar(segundo).getStatus()).isEqualTo(204);
    }

    // ---------- Peticiones ----------

    private MockHttpServletResponse registrarse() throws Exception {
        return pedir(post("/api/v1/auth/registro"), """
                {"nombre":"Ana","correo":"%s","contrasena":"%s","aceptaPolitica":true,"versionPolitica":"1.0"}
                """.formatted(correo, CONTRASENA));
    }

    private MockHttpServletResponse iniciarSesion() throws Exception {
        return pedir(post("/api/v1/auth/inicio-sesion"),
                "{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, CONTRASENA));
    }

    private MockHttpServletResponse verificar(String token) throws Exception {
        return pedir(post("/api/v1/auth/verificacion-correo"), "{\"token\":\"%s\"}".formatted(token));
    }

    private MockHttpServletResponse reenviar(String tokenDeAcceso) throws Exception {
        return mvc.perform(post("/api/v1/auth/verificacion-correo/reenvio")
                .header("Authorization", "Bearer " + tokenDeAcceso)).andReturn().getResponse();
    }

    private MockHttpServletResponse pedir(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion, String json)
            throws Exception {
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    private static JsonNode cuerpo(MockHttpServletResponse respuesta) throws Exception {
        return JSON.readTree(respuesta.getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------- Buzón de pruebas ----------

    /* Espera, como mucho 10 segundos, a que lleguen al menos esa cantidad de correos a la persona. */
    private List<String> esperarCorreos(int cuantos) throws Exception {
        Instant limite = Instant.now().plus(Duration.ofSeconds(10));
        List<String> enlaces = enlacesRecibidos();
        while (enlaces.size() < cuantos && Instant.now().isBefore(limite)) {
            Thread.sleep(200);
            enlaces = enlacesRecibidos();
        }
        assertThat(enlaces).as("correos recibidos").hasSizeGreaterThanOrEqualTo(cuantos);
        return enlaces;
    }

    /* Texto de cada correo recibido por la persona, leído de la API de Mailpit. */
    private List<String> enlacesRecibidos() throws Exception {
        String base = apiDeMailpit();
        List<String> textos = new ArrayList<>();
        for (JsonNode resumen : JSON.readTree(obtener(base + "messages")).get("messages")) {
            if (correo.equals(resumen.get("To").get(0).get("Address").asString())) {
                textos.add(JSON.readTree(obtener(base + "message/" + resumen.get("ID").asString()))
                        .get("Text").asString());
            }
        }
        return textos;
    }

    private static String tokenDelEnlace(String textoDelCorreo) {
        Matcher encontrado = TOKEN_EN_EL_ENLACE.matcher(textoDelCorreo);
        assertThat(encontrado.find()).as("el correo lleva el enlace de verificación").isTrue();
        return encontrado.group(1);
    }

    private static String obtener(String direccion) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(direccion)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        }
    }
}