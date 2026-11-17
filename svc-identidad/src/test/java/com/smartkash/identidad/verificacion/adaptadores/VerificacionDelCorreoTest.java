package com.smartkash.identidad.verificacion.adaptadores;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.registro.aplicacion.DatosDeRegistro;
import com.smartkash.identidad.registro.aplicacion.RegistrarUsuario;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import com.smartkash.identidad.verificacion.aplicacion.EmitirEnlaceDeVerificacion;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba la verificación del correo con PostgreSQL real: el repositorio de tokens, la
 * activación de la cuenta, la transacción que las une y la ruta web.
 *
 * <p>Qué hace: arranca el servicio completo con un PostgreSQL desechable. Cada prueba crea su
 * propia cuenta y sus propios tokens. Las peticiones web pasan por la cadena de filtros real.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
@Testcontainers
class VerificacionDelCorreoTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18").withInitScript("db/roles-de-prueba.sql");

    private static final String RUTA = "/api/v1/auth/verificacion-correo";
    private static final String RUTA_REENVIO = "/api/v1/auth/verificacion-correo/reenvio";
    private static final String HASH = "$argon2id$v=19$m=1,t=1,p=1$c2FsdGRlcHJ1ZWJh$aGFzaGRlcHJ1ZWJh";
    private static final int HILOS = 10;

    /** Contraseña inventada que cumple las reglas del registro. */
    private static final String CONTRASENA = "una frase larga y dificil 2026";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RepositorioTokensDeUnUso tokens;

    @Autowired
    private RepositorioUsuarios usuarios;

    @Autowired
    private UnidadDeTrabajo unidad;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmisorTokenAcceso emisorTokens;

    @Autowired
    private RegistrarUsuario registrarUsuario;

    @Autowired
    private PlatformTransactionManager gestorDeTransacciones;

    /*
     * Un doble del enviador: sustituye al real para que las pruebas no necesiten un servidor de
     * correo y puedan comprobar a quién se envía. El envío real tiene su propia prueba.
     */
    @MockitoBean
    private EnviadorCorreos enviador;

    /*
     * Un espía del caso de uso real que emite los enlaces: hace su trabajo de verdad, pero deja
     * comprobar cuándo se le llamó.
     */
    @MockitoSpyBean
    private EmitirEnlaceDeVerificacion emitirEnlace;

    private MockMvc mvc;
    private Usuario cuenta;
    private Instant ahora;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        String correo = "persona." + UUID.randomUUID() + "@correo.com";
        cuenta = Usuario.nuevo("Ana", correo, new CorreoNormalizado(correo), HASH);
        usuarios.guardar(cuenta);
        ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    // ---------- Repositorio de tokens ----------

    /** Un token guardado se encuentra por su huella con todos sus datos, y con su tipo en la tabla. */
    @Test
    void guarda_y_encuentra_un_token_por_su_huella() {
        TokenVerificacion token = TokenVerificacion.nuevo(cuenta.id(), huellaAlAzar(), ahora);

        tokens.guardar(token);

        assertThat(tokens.buscarPorHuella(token.huella())).contains(token);
        assertThat(jdbc.queryForObject("SELECT tipo FROM tokens_un_solo_uso WHERE id = ?", String.class, token.id()))
                .isEqualTo("VERIFICACION_CORREO");
    }

    /** Una huella que no está guardada no encuentra nada. */
    @Test
    void una_huella_desconocida_no_encuentra_nada() {
        assertThat(tokens.buscarPorHuella(huellaAlAzar())).isEmpty();
    }

    /** El primer intento de marcarlo lo consigue; el segundo no. */
    @Test
    void un_token_solo_se_puede_marcar_una_vez() {
        TokenVerificacion token = guardado(ahora.minus(Duration.ofHours(1)));

        assertThat(tokens.marcarComoUsado(token.usar(ahora))).isTrue();
        assertThat(tokens.marcarComoUsado(token.usar(ahora.plusSeconds(1)))).isFalse();
        assertThat(tokens.buscarPorHuella(token.huella()).orElseThrow().usadoEn()).isEqualTo(ahora);
    }

    /**
     * Diez peticiones presentan el mismo enlace a la vez: exactamente una consigue marcarlo. Las
     * demás reciben {@code false}, aunque todas lo leyeron sin usar.
     */
    @Test
    void con_usos_simultaneos_solo_uno_lo_consigue() throws Exception {
        TokenVerificacion token = guardado(ahora.minus(Duration.ofHours(1)));
        TokenVerificacion usado = token.usar(ahora);

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(HILOS);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < HILOS; i++) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return tokens.marcarComoUsado(usado);
                }));
            }
            salida.countDown();
            int conseguidos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    conseguidos++;
                }
            }
            assertThat(conseguidos).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    /**
     * Se devuelven, de la más antigua a la más reciente, las fechas de emisión de los tokens de
     * esa cuenta emitidos desde el momento indicado; ni los anteriores ni los de otras cuentas.
     */
    @Test
    void devuelve_los_emitidos_desde_un_momento() {
        guardado(ahora.minus(Duration.ofMinutes(90)));
        guardado(ahora.minus(Duration.ofMinutes(10)));
        guardado(ahora.minus(Duration.ofMinutes(30)));
        Usuario otra = otraCuenta();
        tokens.guardar(TokenVerificacion.nuevo(otra.id(), huellaAlAzar(), ahora.minus(Duration.ofMinutes(5))));

        assertThat(tokens.emitidosDesde(cuenta.id(), ahora.minus(Duration.ofHours(1))))
                .containsExactly(ahora.minus(Duration.ofMinutes(30)), ahora.minus(Duration.ofMinutes(10)));
    }

    /**
     * Invalidar los pendientes deja sin valor los tokens sin usar de esa cuenta, conserva el
     * momento de uso de los ya usados y no toca los de otras cuentas.
     */
    @Test
    void invalidar_los_pendientes_solo_afecta_a_los_sin_usar_de_esa_cuenta() {
        TokenVerificacion pendiente = guardado(ahora.minus(Duration.ofMinutes(30)));
        TokenVerificacion yaUsado = guardado(ahora.minus(Duration.ofMinutes(20)));
        Instant usoAnterior = ahora.minus(Duration.ofMinutes(10));
        tokens.marcarComoUsado(yaUsado.usar(usoAnterior));
        Usuario otra = otraCuenta();
        TokenVerificacion deOtra = TokenVerificacion.nuevo(otra.id(), huellaAlAzar(), ahora.minus(Duration.ofMinutes(5)));
        tokens.guardar(deOtra);

        tokens.invalidarPendientes(cuenta.id(), ahora);

        assertThat(tokens.buscarPorHuella(pendiente.huella()).orElseThrow().utilizable(ahora)).isFalse();
        assertThat(tokens.buscarPorHuella(yaUsado.huella()).orElseThrow().usadoEn()).isEqualTo(usoAnterior);
        assertThat(tokens.buscarPorHuella(deOtra.huella()).orElseThrow().utilizable(ahora)).isTrue();
    }

    // ---------- Cuenta y transacción ----------

    /** Verificar la cuenta la activa y anota el momento. */
    @Test
    void marcar_verificada_activa_la_cuenta_y_anota_el_momento() {
        usuarios.marcarVerificada(cuenta.id(), ahora);

        Map<String, Object> fila = filaDeLaCuenta();
        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.get("verificado_en")).isEqualTo(Timestamp.from(ahora));
    }

    /**
     * Una cuenta en periodo de gracia no cambia de estado al verificar: solo se anota la fecha.
     * Una ya verificada conserva su primera fecha de verificación.
     */
    @Test
    void marcar_verificada_no_cambia_otros_estados_ni_la_primera_fecha() {
        jdbc.update("UPDATE usuarios SET estado = 'PENDIENTE_ELIMINACION' WHERE id = ?", cuenta.id());

        usuarios.marcarVerificada(cuenta.id(), ahora);
        usuarios.marcarVerificada(cuenta.id(), ahora.plusSeconds(60));

        Map<String, Object> fila = filaDeLaCuenta();
        assertThat(fila.get("estado")).isEqualTo("PENDIENTE_ELIMINACION");
        assertThat(fila.get("verificado_en")).isEqualTo(Timestamp.from(ahora));
    }

    /** Si algo falla dentro de la unidad de trabajo, se deshace todo lo hecho en ella. */
    @Test
    void si_falla_la_unidad_de_trabajo_no_queda_nada() {
        TokenVerificacion token = guardado(ahora.minus(Duration.ofHours(1)));

        assertThatThrownBy(() -> unidad.ejecutar(() -> {
            tokens.marcarComoUsado(token.usar(ahora));
            usuarios.marcarVerificada(cuenta.id(), ahora);
            throw new IllegalStateException("fallo simulado");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(tokens.buscarPorHuella(token.huella()).orElseThrow().usadoEn()).isNull();
        assertThat(filaDeLaCuenta().get("estado")).isEqualTo("PENDIENTE_VERIFICACION");
    }

    // ---------- Ruta web ----------

    /** Con un enlace válido responde 204 sin cuerpo y la cuenta queda activa. */
    @Test
    void con_un_enlace_valido_responde_204_y_activa_la_cuenta() throws Exception {
        String valor = guardadoConValor();

        MockHttpServletResponse respuesta = verificar(valor);

        assertThat(respuesta.getStatus()).isEqualTo(204);
        assertThat(respuesta.getContentAsString()).isEmpty();
        assertThat(filaDeLaCuenta().get("estado")).isEqualTo("ACTIVA");
    }

    /**
     * Un enlace ya usado y uno que nunca existió reciben exactamente la misma respuesta: 400 de
     * regla de negocio con el código {@code ENLACE_NO_VALIDO}.
     */
    @Test
    void enlace_usado_e_inexistente_responden_igual() throws Exception {
        String valor = guardadoConValor();
        verificar(valor);

        MockHttpServletResponse porUsado = verificar(valor);
        MockHttpServletResponse porInexistente = verificar("enlace-inventado-" + UUID.randomUUID());

        assertThat(porUsado.getStatus()).isEqualTo(400);
        assertThat(porUsado.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"type\":\"/problemas/regla-negocio\"")
                .contains("\"codigo\":\"ENLACE_NO_VALIDO\"");
        assertThat(huellaDeRespuesta(porInexistente)).isEqualTo(huellaDeRespuesta(porUsado));
    }

    /** Un token demasiado corto, ausente o con un campo de más responde 400 de validación. */
    @Test
    void una_solicitud_mal_formada_responde_400() throws Exception {
        for (String cuerpo : List.of("{}", "{\"token\":\"corto\"}",
                "{\"token\":\"" + "x".repeat(201) + "\"}",
                "{\"token\":\"" + "x".repeat(30) + "\",\"usuarioId\":\"" + cuenta.id() + "\"}")) {
            MockHttpServletResponse respuesta = mvc.perform(post(RUTA)
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn().getResponse();
            assertThat(respuesta.getStatus()).as(cuerpo).isEqualTo(400);
            assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).contains("/problemas/validacion");
        }
    }

    // ---------- Reenvío ----------

    /** Sin token de acceso, el reenvío responde 401. */
    @Test
    void reenviar_sin_token_responde_401() throws Exception {
        MockHttpServletResponse respuesta = mvc.perform(post(RUTA_REENVIO)).andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(401);
        verifyNoInteractions(enviador);
    }

    /**
     * Con el token de una cuenta pendiente responde 202 con un mensaje, guarda un enlace nuevo y
     * envía el correo a esa cuenta.
     */
    @Test
    void reenviar_a_una_cuenta_pendiente_envia_un_enlace() throws Exception {
        MockHttpServletResponse respuesta = reenviar(cuenta);

        assertThat(respuesta.getStatus()).isEqualTo(202);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).startsWith("{\"mensaje\":\"");
        assertThat(enlacesDe(cuenta)).isEqualTo(1);
        verify(enviador).enviarVerificacion(argThat(correo -> correo.destinatario().equals(cuenta.correoOriginal())));
    }

    /**
     * Una cuenta ya verificada recibe exactamente la misma respuesta, pero no se le envía nada.
     */
    @Test
    void cuenta_verificada_y_pendiente_reciben_la_misma_respuesta() throws Exception {
        Usuario verificada = otraCuenta();
        usuarios.marcarVerificada(verificada.id(), ahora);

        MockHttpServletResponse paraPendiente = reenviar(cuenta);
        MockHttpServletResponse paraVerificada = reenviar(verificada);

        assertThat(huellaDeRespuesta(paraVerificada)).isEqualTo(huellaDeRespuesta(paraPendiente));
        assertThat(enlacesDe(verificada)).isZero();
        verify(enviador, never()).enviarVerificacion(
                argThat(correo -> correo.destinatario().equals(verificada.correoOriginal())));
    }

    /** El cuarto reenvío en una hora responde 429 con el tiempo de espera. */
    @Test
    void el_cuarto_reenvio_en_una_hora_responde_429() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(reenviar(cuenta).getStatus()).isEqualTo(202);
        }

        MockHttpServletResponse respuesta = reenviar(cuenta);

        assertThat(respuesta.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(respuesta.getHeader("Retry-After"))).isBetween(3500L, 3600L);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"type\":\"/problemas/demasiadas-solicitudes\"");
        assertThat(enlacesDe(cuenta)).isEqualTo(3);
    }

    /**
     * La cuenta sale del token de acceso. Aunque el cuerpo nombre otra cuenta, se ignora: el
     * enlace es para la dueña del token.
     */
    @Test
    void la_cuenta_sale_del_token_y_no_del_cuerpo() throws Exception {
        Usuario otra = otraCuenta();

        mvc.perform(post(RUTA_REENVIO)
                .header("Authorization", "Bearer " + tokenDeAcceso(cuenta))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuarioId\":\"" + otra.id() + "\"}"));

        assertThat(enlacesDe(cuenta)).isEqualTo(1);
        assertThat(enlacesDe(otra)).isZero();
    }

    // ---------- Primer enlace al registrarse ----------

    /**
     * Al registrarse con un correo nuevo, se emite el primer enlace y se envía a ese correo. El
     * envío ocurre en otro hilo, así que la prueba espera a que llegue (como mucho 5 segundos).
     */
    @Test
    void registrarse_envia_el_primer_enlace() throws Exception {
        String correo = "nueva." + UUID.randomUUID() + "@correo.com";

        MockHttpServletResponse respuesta = registrarse(correo);

        assertThat(respuesta.getStatus()).isEqualTo(202);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("Si el correo no tenía cuenta, te enviamos un mensaje para verificarlo.");
        verify(enviador, timeout(5_000)).enviarVerificacion(argThat(c -> c.destinatario().equals(correo)));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM tokens_un_solo_uso t JOIN usuarios u ON u.id = t.usuario_id
                WHERE u.email_normalizado = ?""", Integer.class, correo)).isEqualTo(1);
    }

    /** Registrarse otra vez con el mismo correo no envía nada. */
    @Test
    void registrarse_con_un_correo_repetido_no_envia_nada() throws Exception {
        String correo = "repetida." + UUID.randomUUID() + "@correo.com";
        registrarse(correo);
        verify(enviador, timeout(5_000)).enviarVerificacion(argThat(c -> c.destinatario().equals(correo)));
        clearInvocations(enviador);

        registrarse(correo.toUpperCase());

        verify(enviador, after(1_500).never()).enviarVerificacion(any());
    }

    /**
     * Si la transacción del registro se deshace, la cuenta no existe y ni siquiera se intenta
     * emitir el enlace: el escuchador solo actúa después de confirmar.
     */
    @Test
    void si_el_registro_se_deshace_ni_se_intenta_emitir() {
        String correo = "deshecha." + UUID.randomUUID() + "@correo.com";

        new TransactionTemplate(gestorDeTransacciones).executeWithoutResult(estado -> {
            registrarUsuario.ejecutar(new DatosDeRegistro("Ana", correo, CONTRASENA, true, "1.0"));
            estado.setRollbackOnly();
        });

        verify(emitirEnlace, after(1_500).never()).emitir(any());
        verify(enviador, never()).enviarVerificacion(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email_normalizado = ?",
                Integer.class, correo)).isZero();
    }

    /**
     * Mientras la transacción del registro sigue abierta no se emite nada; en cuanto se
     * confirma, se emite el enlace de esa cuenta.
     */
    @Test
    void el_enlace_se_emite_solo_despues_de_confirmar() {
        String correo = "confirmada." + UUID.randomUUID() + "@correo.com";

        new TransactionTemplate(gestorDeTransacciones).executeWithoutResult(estado -> {
            registrarUsuario.ejecutar(new DatosDeRegistro("Ana", correo, CONTRASENA, true, "1.0"));
            // La transacción sigue abierta: aunque pase un segundo, todavía no se emite nada.
            verify(emitirEnlace, after(1_000).never()).emitir(any());
        });

        verify(emitirEnlace, timeout(5_000)).emitir(argThat(cuenta -> cuenta.correo().valor().equals(correo)));
    }

    /**
     * La respuesta del registro no espera al correo: aunque el servidor de correo tarde 3
     * segundos, el registro responde mucho antes, y el correo sale después.
     */
    @Test
    void la_respuesta_del_registro_no_espera_al_correo() throws Exception {
        doAnswer(invocacion -> {
            Thread.sleep(3_000);
            return null;
        }).when(enviador).enviarVerificacion(any());
        String correo = "lenta." + UUID.randomUUID() + "@correo.com";

        long inicio = System.nanoTime();
        MockHttpServletResponse respuesta = registrarse(correo);
        Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(respuesta.getStatus()).isEqualTo(202);
        assertThat(duracion).isLessThan(Duration.ofMillis(2_000));
        verify(enviador, timeout(6_000)).enviarVerificacion(argThat(c -> c.destinatario().equals(correo)));
    }

    // ---------- Ayudas ----------

    private MockHttpServletResponse registrarse(String correo) throws Exception {
        return mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nombre":"Ana","correo":"%s","contrasena":"%s","aceptaPolitica":true,"versionPolitica":"1.0"}
                        """.formatted(correo, CONTRASENA)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse reenviar(Usuario quien) throws Exception {
        return mvc.perform(post(RUTA_REENVIO).header("Authorization", "Bearer " + tokenDeAcceso(quien)))
                .andReturn().getResponse();
    }

    private String tokenDeAcceso(Usuario quien) {
        return emisorTokens.emitir(quien, Instant.now().truncatedTo(ChronoUnit.SECONDS)).valor();
    }

    private int enlacesDe(Usuario quien) {
        return jdbc.queryForObject("SELECT count(*) FROM tokens_un_solo_uso WHERE usuario_id = ?",
                Integer.class, quien.id());
    }

    private MockHttpServletResponse verificar(String valor) throws Exception {
        return mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + valor + "\"}")).andReturn().getResponse();
    }

    private TokenVerificacion guardado(Instant creadoEn) {
        TokenVerificacion token = TokenVerificacion.nuevo(cuenta.id(), huellaAlAzar(), creadoEn);
        tokens.guardar(token);
        return token;
    }

    /* Guarda un token emitido hace una hora y devuelve el valor que iría en el enlace. */
    private String guardadoConValor() {
        String valor = "valor-de-prueba-" + UUID.randomUUID();
        tokens.guardar(TokenVerificacion.nuevo(cuenta.id(), sha256(valor), Instant.now().minus(Duration.ofHours(1))));
        return valor;
    }

    private Usuario otraCuenta() {
        String correo = "otra." + UUID.randomUUID() + "@correo.com";
        Usuario otra = Usuario.nuevo("Luis", correo, new CorreoNormalizado(correo), HASH);
        usuarios.guardar(otra);
        return otra;
    }

    private Map<String, Object> filaDeLaCuenta() {
        return jdbc.queryForMap("SELECT estado, verificado_en FROM usuarios WHERE id = ?", cuenta.id());
    }

    private static String huellaAlAzar() {
        return sha256(UUID.randomUUID().toString());
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String huellaDeRespuesta(MockHttpServletResponse respuesta) throws Exception {
        Map<String, List<String>> cabeceras = new TreeMap<>();
        for (String nombre : respuesta.getHeaderNames()) {
            cabeceras.put(nombre, respuesta.getHeaders(nombre));
        }
        return respuesta.getStatus() + "\n" + cabeceras + "\n"
                + respuesta.getContentAsString(StandardCharsets.UTF_8);
    }
}