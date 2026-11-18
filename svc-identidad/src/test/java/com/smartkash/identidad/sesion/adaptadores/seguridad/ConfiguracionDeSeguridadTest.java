package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.smartkash.identidad.ClavesDePrueba;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import com.smartkash.nucleo.web.errores.ManejadorErroresComun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprueba la cadena de filtros de seguridad: qué rutas son públicas, qué tokens se aceptan
 * y cómo se responde cuando no hay uno válido.
 *
 * <p>Qué hace: {@code @SpringJUnitWebConfig} arranca solo las clases indicadas en
 * {@link Contexto}: la configuración de seguridad, la de la firma, los manejadores de errores
 * y un controlador que solo existe en esta prueba ({@link RutasDePrueba}). No hay base de
 * datos ni casos de uso reales: lo que se prueba es el filtro que va delante de todos los
 * controladores. {@code springSecurity()} hace que las peticiones simuladas pasen por la
 * cadena de filtros real.</p>
 *
 * <p>Los tokens se fabrican de varias formas para cubrir los ataques habituales: firmados por
 * el servicio, caducados, con la firma alterada, sin firma ({@code alg=none}), firmados con
 * otra clave y con otro emisor o destinatario.</p>
 */
@SpringJUnitWebConfig(ConfiguracionDeSeguridadTest.Contexto.class)
class ConfiguracionDeSeguridadTest {

    private static final String EMISOR = "https://identidad.pruebas.invalid";
    private static final Path CLAVE = ClavesDePrueba.archivoTemporal(
            ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate()));

    private static final String RUTA_PROTEGIDA = "/api/v1/prueba/protegida";

    /** Rutas públicas: el método y la ruta. */
    private static final List<RequestBuilder> RUTAS_PUBLICAS = List.of(
            post("/api/v1/auth/registro"),
            post("/api/v1/auth/inicio-sesion"),
            post("/api/v1/auth/verificacion-correo"),
            get("/.well-known/jwks.json"));

    /** Las clases que arranca la prueba; nada más. */
    @Configuration
    @EnableWebMvc
    @Import({ConfiguracionDeSeguridad.class, ConfiguracionDeLaFirma.class,
            ManejadorErroresDeAutenticacion.class, ManejadorErroresComun.class, RutasDePrueba.class})
    static class Contexto {
    }

    /** Controlador de prueba: una ruta protegida y las rutas públicas, que responden "ok". */
    @RestController
    static class RutasDePrueba {

        @GetMapping(RUTA_PROTEGIDA)
        Map<String, Object> protegida(Principal quien) {
            JwtAuthenticationToken autenticacion = (JwtAuthenticationToken) quien;
            return Map.of(
                    "cuenta", autenticacion.getToken().getSubject(),
                    "autoridades", autenticacion.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority).toList());
        }

        @PostMapping({"/api/v1/auth/registro", "/api/v1/auth/inicio-sesion",
                "/api/v1/auth/verificacion-correo"})
        String publicaPost() {
            return "ok";
        }

        @GetMapping("/.well-known/jwks.json")
        String publicaGet() {
            return "ok";
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("identidad.token.clave-firma-ruta", CLAVE::toString);
        registro.add("identidad.token.emisor", () -> EMISOR);
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private EmisorTokenAcceso emisor;

    @Autowired
    private ClaveDeFirma claveDelServicio;

    private MockMvc mvc;

    private final Usuario cuenta = new Usuario(UUID.randomUUID(), "Ana", "ana@correo.com",
            new CorreoNormalizado("ana@correo.com"), "hash-cualquiera", Rol.USUARIO, EstadoCuenta.ACTIVA);

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    // ---------- Sin token o con un token válido ----------

    /** Sin token, una ruta protegida responde 401 en formato RFC 9457 y pide un token Bearer. */
    @Test
    void sin_token_responde_401_en_formato_estandar() throws Exception {
        MockHttpServletResponse respuesta = pedir(get(RUTA_PROTEGIDA));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        String cuerpo = respuesta.getContentAsString(StandardCharsets.UTF_8);
        assertThat(cuerpo)
                .contains("\"type\":\"/problemas/no-autenticado\"")
                .contains("\"title\":\"Se requiere autenticación\"")
                .contains("\"status\":401");
    }

    /**
     * Con un token emitido por el servicio, la ruta responde y el controlador recibe la cuenta
     * sacada del token y su rol como autoridad.
     */
    @Test
    void con_un_token_valido_entra_y_la_cuenta_sale_del_token() throws Exception {
        String token = emisor.emitir(cuenta, ahora()).valor();

        MockHttpServletResponse respuesta = pedir(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token));

        assertThat(respuesta.getStatus()).isEqualTo(200);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"cuenta\":\"" + cuenta.id() + "\"")
                .contains("ROLE_USUARIO");
    }

    // ---------- Tokens que se rechazan ----------

    /** Un token caducado hace más de un minuto se rechaza. */
    @Test
    void un_token_caducado_se_rechaza() throws Exception {
        String token = emisor.emitir(cuenta, ahora().minus(Duration.ofMinutes(20))).valor();

        assertThat(conToken(token).getStatus()).isEqualTo(401);
    }

    /** Si se cambia un carácter de la firma, la firma ya no cuadra y el token se rechaza. */
    @Test
    void un_token_con_la_firma_alterada_se_rechaza() throws Exception {
        assertThat(conToken(firmaAlterada()).getStatus()).isEqualTo(401);
    }

    /** Un token sin firma ({@code alg=none}) se rechaza aunque su contenido sea correcto. */
    @Test
    void un_token_sin_firma_se_rechaza() throws Exception {
        assertThat(conToken(sinFirma()).getStatus()).isEqualTo(401);
    }

    /** Un token firmado con otra clave se rechaza: solo vale la clave del servicio. */
    @Test
    void un_token_firmado_con_otra_clave_se_rechaza() throws Exception {
        assertThat(conToken(deOtraClave()).getStatus()).isEqualTo(401);
    }

    /** Un token emitido por otro emisor se rechaza aunque la firma sea válida. */
    @Test
    void un_token_de_otro_emisor_se_rechaza() throws Exception {
        assertThat(conToken(deOtroEmisor()).getStatus()).isEqualTo(401);
    }

    /** Un token dirigido a otro destinatario se rechaza aunque la firma sea válida. */
    @Test
    void un_token_para_otro_destinatario_se_rechaza() throws Exception {
        assertThat(conToken(paraOtroDestinatario()).getStatus()).isEqualTo(401);
    }

    /**
     * Todas las respuestas de rechazo son idénticas: mismo código, mismas cabeceras y mismo
     * cuerpo. Así la respuesta no revela por qué se rechazó el token.
     */
    @Test
    void todos_los_rechazos_son_identicos() throws Exception {
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("caducado", emisor.emitir(cuenta, ahora().minus(Duration.ofMinutes(20))).valor());
        casos.put("firma alterada", firmaAlterada());
        casos.put("sin firma", sinFirma());
        casos.put("otra clave", deOtraClave());
        casos.put("otro emisor", deOtroEmisor());
        casos.put("otro destinatario", paraOtroDestinatario());
        casos.put("basura", "esto-no-es-un-token");

        String referencia = huellaDeRespuesta(pedir(get(RUTA_PROTEGIDA)));
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            assertThat(huellaDeRespuesta(conToken(caso.getValue())))
                    .as("respuesta para un token %s", caso.getKey())
                    .isEqualTo(referencia);
        }
    }

    // ---------- Rutas públicas ----------

    /** Las rutas públicas responden sin token. */
    @Test
    void las_rutas_publicas_no_exigen_token() throws Exception {
        for (RequestBuilder ruta : RUTAS_PUBLICAS) {
            assertThat(pedir(ruta).getStatus()).isEqualTo(200);
        }
    }

    /**
     * En una ruta pública la cabecera de autorización se ignora: un token caducado o inválido
     * no impide, por ejemplo, volver a iniciar sesión.
     */
    @Test
    void en_las_rutas_publicas_se_ignora_un_token_invalido() throws Exception {
        String caducado = emisor.emitir(cuenta, ahora().minus(Duration.ofMinutes(20))).valor();

        assertThat(pedir(post("/api/v1/auth/inicio-sesion").header("Authorization", "Bearer " + caducado))
                .getStatus()).isEqualTo(200);
        assertThat(pedir(post("/api/v1/auth/registro").header("Authorization", "Bearer basura"))
                .getStatus()).isEqualTo(200);
    }

    /** Solo es público el método declarado: un GET a la ruta de inicio de sesión exige token. */
    @Test
    void solo_el_metodo_declarado_es_publico() throws Exception {
        assertThat(pedir(get("/api/v1/auth/inicio-sesion")).getStatus()).isEqualTo(401);
    }

    /** Ninguna respuesta crea una sesión de servidor: cada petición se valida sola. */
    @Test
    void no_se_crea_sesion_de_servidor() throws Exception {
        String token = emisor.emitir(cuenta, ahora()).valor();

        MvcResult resultado = mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    // ---------- Tamaño del cuerpo ----------

    /**
     * Un cuerpo de más de 16 KiB se rechaza con el error de validación estándar, sin llegar al
     * controlador. Se prueba en una ruta pública para que nada más pueda rechazarlo.
     */
    @Test
    void un_cuerpo_demasiado_grande_responde_400_en_formato_estandar() throws Exception {
        MockHttpServletResponse respuesta = pedir(post("/api/v1/auth/registro")
                .contentType("application/json").content(new byte[16 * 1024 + 1]));

        assertThat(respuesta.getStatus()).isEqualTo(400);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8))
                .contains("\"type\":\"/problemas/validacion\"")
                .contains(TopeDelCuerpo.DETALLE)
                .doesNotContain("ok");
    }

    /** Un cuerpo de exactamente 16 KiB todavía se acepta. */
    @Test
    void un_cuerpo_de_16_kib_se_acepta() throws Exception {
        assertThat(pedir(post("/api/v1/auth/registro")
                .contentType("application/json").content(new byte[16 * 1024])).getStatus()).isEqualTo(200);
    }

    /**
     * El tamaño se mira antes que el token: un cuerpo enorme hacia una ruta protegida se
     * rechaza por su tamaño sin gastar tiempo en comprobar ninguna firma.
     */
    @Test
    void el_tamano_se_comprueba_antes_que_el_token() throws Exception {
        MockHttpServletResponse respuesta = pedir(post(RUTA_PROTEGIDA)
                .contentType("application/json").content(new byte[16 * 1024 + 1]));

        assertThat(respuesta.getStatus()).isEqualTo(400);
        assertThat(respuesta.getContentAsString(StandardCharsets.UTF_8)).contains(TopeDelCuerpo.DETALLE);
    }

    // ---------- Ayudas ----------

    private MockHttpServletResponse pedir(RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion).andReturn().getResponse();
    }

    private MockHttpServletResponse conToken(String token) throws Exception {
        return pedir(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token));
    }

    /* Código, cabeceras y cuerpo de una respuesta, para comparar respuestas enteras. */
    private static String huellaDeRespuesta(MockHttpServletResponse respuesta) throws Exception {
        Map<String, List<String>> cabeceras = new java.util.TreeMap<>();
        for (String nombre : respuesta.getHeaderNames()) {
            cabeceras.put(nombre, respuesta.getHeaders(nombre));
        }
        return respuesta.getStatus() + "\n" + cabeceras + "\n"
                + respuesta.getContentAsString(StandardCharsets.UTF_8);
    }

    private static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String firmaAlterada() {
        String token = emisor.emitir(cuenta, ahora()).valor();
        int punto = token.lastIndexOf('.');
        // Se cambia un carácter del centro de la firma; el último podría no alterar ningún bit.
        int posicion = punto + (token.length() - punto) / 2;
        char nuevo = token.charAt(posicion) == 'A' ? 'B' : 'A';
        return token.substring(0, posicion) + nuevo + token.substring(posicion + 1);
    }

    private String sinFirma() {
        String contenidoValido = emisor.emitir(cuenta, ahora()).valor().split("\\.")[1];
        String cabecera = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        return cabecera + "." + contenidoValido + ".";
    }

    private String deOtraClave() {
        ClaveDeFirma otra = ClaveDeFirma.desdeArchivo(ClavesDePrueba.archivoTemporal(
                ClavesDePrueba.pem(ClavesDePrueba.rsa(3072).getPrivate())));
        return new EmisorJwtRs256(otra, EMISOR).emitir(cuenta, ahora()).valor();
    }

    private String deOtroEmisor() {
        return new EmisorJwtRs256(claveDelServicio, "https://otro-emisor.invalid")
                .emitir(cuenta, ahora()).valor();
    }

    private String paraOtroDestinatario() {
        Instant ahora = ahora();
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .audience(List.of("otro-servicio"))
                .subject(cuenta.id().toString())
                .claim("roles", List.of("USUARIO"))
                .id(UUID.randomUUID().toString())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(15)))
                .build();
        return firmar(claveDelServicio.paraFirmar(), datos);
    }

    private static String firmar(RSAKey clave, JwtClaimsSet datos) {
        NimbusJwtEncoder firmador = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(clave)));
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(clave.getKeyID()).build();
        return firmador.encode(JwtEncoderParameters.from(cabecera, datos)).getTokenValue();
    }
}