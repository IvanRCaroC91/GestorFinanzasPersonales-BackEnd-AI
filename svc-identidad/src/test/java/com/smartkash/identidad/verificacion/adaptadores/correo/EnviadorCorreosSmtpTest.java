package com.smartkash.identidad.verificacion.adaptadores.correo;

import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba el envío del correo de verificación contra un servidor de correo de verdad.
 *
 * <p>Qué hace: arranca en Docker un Mailpit, un servidor de correo para desarrollo que acepta
 * todo lo que recibe y no lo entrega a nadie. El adaptador le envía el correo por SMTP, igual
 * que haría con un proveedor real, y la prueba lee lo que llegó a través de la API HTTP de
 * Mailpit. No arranca Spring.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@Testcontainers
class EnviadorCorreosSmtpTest {

    private static final int PUERTO_SMTP = 1025;
    private static final int PUERTO_WEB = 8025;
    private static final String REMITENTE = "no-responder@pruebas.invalid";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.33.1"))
            .withExposedPorts(PUERTO_SMTP, PUERTO_WEB)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(PUERTO_WEB));

    /**
     * El correo llega a su destinatario, desde el remitente configurado, con el asunto fijo y
     * con el nombre y el enlace en el texto. Las tildes llegan intactas.
     */
    @Test
    void envia_el_correo_con_el_enlace() throws Exception {
        String destinatario = "persona." + UUID.randomUUID() + "@correo.com";
        String enlace = "https://app.ejemplo/verificar-correo#token=valor-de-prueba-" + UUID.randomUUID();

        enviador(MAILPIT.getMappedPort(PUERTO_SMTP))
                .enviarVerificacion(new CorreoDeVerificacion(destinatario, "José Núñez", enlace));

        JsonNode mensaje = mensajePara(destinatario);
        assertThat(mensaje.get("From").get("Address").asString()).isEqualTo(REMITENTE);
        assertThat(mensaje.get("To").get(0).get("Address").asString()).isEqualTo(destinatario);
        assertThat(mensaje.get("Subject").asString()).isEqualTo(EnviadorCorreosSmtp.ASUNTO);
        assertThat(mensaje.get("Text").asString())
                .contains("José Núñez")
                .contains(enlace)
                .contains("24 horas");
        assertThat(mensaje.get("HTML").asString()).isEmpty();
    }

    /** Un destinatario que no es una dirección de correo provoca un error y no se envía nada. */
    @Test
    void un_destinatario_mal_formado_lanza_un_error() {
        CorreoDeVerificacion correo = new CorreoDeVerificacion(
                "esto no es un correo", "Ana", "https://app.ejemplo/verificar-correo#token=secreto");

        assertThatThrownBy(() -> enviador(MAILPIT.getMappedPort(PUERTO_SMTP)).enviarVerificacion(correo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageNotContaining("secreto");
    }

    /** Si el servidor de correo no responde, el error sale hacia quien llama. */
    @Test
    void si_el_servidor_no_responde_lanza_un_error() throws Exception {
        int puertoCerrado;
        try (ServerSocket libre = new ServerSocket(0)) {
            puertoCerrado = libre.getLocalPort();
        }
        CorreoDeVerificacion correo = new CorreoDeVerificacion(
                "persona@correo.com", "Ana", "https://app.ejemplo/verificar-correo#token=x");

        assertThatThrownBy(() -> enviador(puertoCerrado).enviarVerificacion(correo))
                .isInstanceOf(RuntimeException.class);
    }

    // ---------- Ayudas ----------

    private static EnviadorCorreosSmtp enviador(int puerto) {
        JavaMailSenderImpl cliente = new JavaMailSenderImpl();
        cliente.setHost(MAILPIT.getHost());
        cliente.setPort(puerto);
        Properties tiempos = new Properties();
        tiempos.put("mail.smtp.connectiontimeout", "5000");
        tiempos.put("mail.smtp.timeout", "5000");
        cliente.setJavaMailProperties(tiempos);
        return new EnviadorCorreosSmtp(cliente, REMITENTE);
    }

    /* Busca en Mailpit el último mensaje enviado a ese destinatario y lo devuelve completo. */
    private static JsonNode mensajePara(String destinatario) throws Exception {
        String base = "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(PUERTO_WEB) + "/api/v1/";
        JsonNode lista = JSON.readTree(obtener(base + "messages"));
        for (JsonNode resumen : lista.get("messages")) {
            if (destinatario.equals(resumen.get("To").get(0).get("Address").asString())) {
                return JSON.readTree(obtener(base + "message/" + resumen.get("ID").asString()));
            }
        }
        throw new AssertionError("No llegó ningún correo a ese destinatario.");
    }

    private static String obtener(String direccion) throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            return http.send(HttpRequest.newBuilder(URI.create(direccion)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        }
    }
}