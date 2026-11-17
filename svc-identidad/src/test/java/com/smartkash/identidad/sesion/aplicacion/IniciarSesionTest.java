package com.smartkash.identidad.sesion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.sesion.dominio.ControlDeIntentos;
import com.smartkash.identidad.sesion.dominio.CredencialesIncorrectas;
import com.smartkash.identidad.sesion.dominio.DemasiadosIntentos;
import com.smartkash.identidad.sesion.dominio.TipoDeContador;
import com.smartkash.identidad.sesion.dominio.TokenAcceso;
import com.smartkash.identidad.sesion.dominio.TokenRenovacion;
import com.smartkash.identidad.sesion.puertos.ConsultaDePoliticas;
import com.smartkash.identidad.sesion.puertos.EmisorTokenAcceso;
import com.smartkash.identidad.sesion.puertos.RepositorioBloqueos;
import com.smartkash.identidad.sesion.puertos.RepositorioTokensRenovacion;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas del caso de uso {@link IniciarSesion}.
 *
 * <p>Qué comprueban: que unas credenciales correctas entregan los dos tokens y guardan solo
 * la huella del de renovación; que una contraseña errónea, un correo sin cuenta y cualquier
 * otro fallo producen exactamente el mismo error, con las mismas llamadas, y suman en los dos
 * contadores; y que un bloqueo en curso frena el intento antes de mirar la contraseña, sin
 * tocar ningún contador.</p>
 *
 * <p>Cómo lo hacen: todos los puertos se sustituyen por versiones falsas, definidas al final
 * del archivo, que guardan en memoria lo que reciben y apuntan cada llamada en una lista
 * común ({@link #llamadas}). Comparar esa lista permite demostrar qué se hizo, en qué orden
 * y, sobre todo, qué no se hizo. El repositorio de bloqueos falso aplica las reglas reales
 * de {@link ControlDeIntentos}, así que los bloqueos de estas pruebas salen de verdad de
 * acumular fallos. La hora la da un reloj de prueba que solo avanza cuando la prueba lo
 * pide.</p>
 */
class IniciarSesionTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T10:00:00Z");
    private static final String CORREO = "ana@correo.com";
    private static final String IP = "203.0.113.7";

    /** Contraseñas inventadas para las pruebas. */
    private static final String CONTRASENA = "una-clave-de-prueba-123";
    private static final String ERRONEA = "otra-clave-de-prueba-456";

    private static final String MENSAJE_GENERICO = "Correo o contraseña incorrectos.";

    /** Lo que se espera ver en las llamadas cuando un intento termina en fallo. */
    private static final List<String> LLAMADAS_DE_UN_FALLO = List.of(
            "huella", "huella", "buscarBloqueo", "buscarBloqueo",
            "buscarCuenta", "comparar", "anotarFallo:CUENTA", "anotarFallo:IP");

    private final List<String> llamadas = new ArrayList<>();

    private final RelojDePrueba reloj = new RelojDePrueba(AHORA);
    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria(llamadas);
    private final BloqueosEnMemoria bloqueos = new BloqueosEnMemoria(llamadas);
    private final TokensRenovacionEnMemoria renovaciones = new TokensRenovacionEnMemoria(llamadas);
    private final EmisorFalso emisor = new EmisorFalso(llamadas);
    private final HasheadorFalso hasheador = new HasheadorFalso(llamadas);
    private final GeneradorFalso generador = new GeneradorFalso();
    private final HuellasFalsas huellas = new HuellasFalsas(llamadas);
    private final PoliticasFalsas politicas = new PoliticasFalsas(llamadas);

    private final IniciarSesion iniciarSesion = new IniciarSesion(
            usuarios, bloqueos, renovaciones, emisor, hasheador, generador, huellas, politicas, reloj);

    private final Usuario ana = cuentaCon(EstadoCuenta.ACTIVA);

    /** Registra la cuenta de Ana y olvida lo que se apuntó al crear el caso de uso. */
    @BeforeEach
    void prepararCuenta() {
        usuarios.agregar(ana);
        llamadas.clear();
    }

    // ---------- Credenciales correctas ----------

    @Test
    @DisplayName("con credenciales correctas entrega el token de acceso firmado para esa cuenta y a esa hora")
    void entregaElTokenDeAcceso() {
        SesionIniciada sesion = iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(emisor.cuentas).containsExactly(ana);
        assertThat(emisor.horas).containsExactly(AHORA);
        assertThat(sesion.tokenAcceso()).isEqualTo(emisor.emitidos.getFirst());
        assertThat(sesion.usuario()).isEqualTo(ana);
    }

    @Test
    @DisplayName("con credenciales correctas entrega el valor del token de renovación, que caduca en 7 días")
    void entregaElTokenDeRenovacion() {
        SesionIniciada sesion = iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(sesion.tokenRenovacion()).isEqualTo(generador.ultimo);
        assertThat(sesion.renovacionExpiraEn()).isEqualTo(AHORA.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("guarda un solo token de renovación, con la huella del valor y nunca el valor")
    void guardaSoloLaHuellaDelTokenDeRenovacion() {
        SesionIniciada sesion = iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(renovaciones.guardados).hasSize(1);
        TokenRenovacion guardado = renovaciones.guardados.getFirst();
        assertThat(guardado.huella()).isEqualTo(sha256(sesion.tokenRenovacion()));
        assertThat(guardado.huella()).isNotEqualTo(sesion.tokenRenovacion());
        assertThat(guardado.usuarioId()).isEqualTo(ana.id());
        assertThat(guardado.emitidoEn()).isEqualTo(AHORA);
        assertThat(guardado.expiraEn()).isEqualTo(sesion.renovacionExpiraEn());
    }

    @Test
    @DisplayName("cada inicio de sesión abre una familia nueva de tokens de renovación")
    void cadaInicioDeSesionAbreUnaFamiliaNueva() {
        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);
        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(renovaciones.guardados).hasSize(2);
        assertThat(renovaciones.guardados.get(0).familiaId())
                .isNotEqualTo(renovaciones.guardados.get(1).familiaId());
    }

    @Test
    @DisplayName("al entrar borra el contador de la cuenta y deja intacto el de la IP")
    void alEntrarBorraElContadorDeLaCuentaPeroNoElDeLaIp() {
        fallar(CORREO, IP);

        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(bloqueos.olvidadas).containsExactly(sha256("CUENTA:" + CORREO));
        assertThat(bloqueos.contadores).doesNotContainKey(sha256("CUENTA:" + CORREO));
        assertThat(bloqueos.contadores.get(sha256("IP:" + IP)).fallos()).isEqualTo(1);
    }

    @Test
    @DisplayName("tras entrar, los fallos anteriores de la cuenta ya no cuentan")
    void trasEntrarLosFallosAnterioresNoCuentan() {
        for (int i = 0; i < 3; i++) {
            fallar(CORREO, IP);
        }
        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        // Sin el borrado, el segundo de estos fallos sería el quinto y bloquearía la cuenta.
        for (int i = 0; i < 4; i++) {
            fallar(CORREO, IP);
        }

        assertThat(iniciarSesion.ejecutar(CORREO, CONTRASENA, IP).usuario()).isEqualTo(ana);
    }

    // ---------- Política de tratamiento pendiente ----------

    @Test
    @DisplayName("si hay una política nueva sin aceptar, la sesión se inicia igual y lo indica")
    void conPoliticaPendienteLoIndica() {
        politicas.pendiente = true;

        SesionIniciada sesion = iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(sesion.requiereAceptarPolitica()).isTrue();
        assertThat(politicas.consultas).containsExactly(new ConsultaDePolitica(ana.id(), AHORA));
    }

    @Test
    @DisplayName("sin política pendiente, la sesión no pide aceptar nada")
    void sinPoliticaPendienteNoPideNada() {
        assertThat(iniciarSesion.ejecutar(CORREO, CONTRASENA, IP).requiereAceptarPolitica()).isFalse();
    }

    @Test
    @DisplayName("si las credenciales fallan, no se consultan las políticas")
    void siFallaNoSeConsultanLasPoliticas() {
        fallar(CORREO, IP);

        assertThat(politicas.consultas).isEmpty();
    }

    // ---------- Contraseña errónea ----------

    @Test
    @DisplayName("con la contraseña errónea lanza el error genérico y suma un fallo a la cuenta y a la IP")
    void contrasenaErroneaSumaEnLosDosContadores() {
        CredencialesIncorrectas error = assertThrows(CredencialesIncorrectas.class,
                () -> iniciarSesion.ejecutar(CORREO, ERRONEA, IP));

        assertThat(error.getMessage()).isEqualTo(MENSAJE_GENERICO);
        assertThat(hasheador.comparaciones)
                .containsExactly(new Comparacion(ERRONEA, ana.hashContrasena()));
        assertThat(bloqueos.anotados).containsExactly(
                new Anotacion(sha256("CUENTA:" + CORREO), TipoDeContador.CUENTA),
                new Anotacion(sha256("IP:" + IP), TipoDeContador.IP));
    }

    @Test
    @DisplayName("con la contraseña errónea no emite tokens, no guarda nada y no borra ningún contador")
    void contrasenaErroneaNoEntregaNada() {
        fallar(CORREO, IP);

        assertThat(emisor.emitidos).isEmpty();
        assertThat(renovaciones.guardados).isEmpty();
        assertThat(bloqueos.olvidadas).isEmpty();
    }

    // ---------- Correo inexistente o cuenta eliminada ----------

    @Test
    @DisplayName("un correo sin cuenta (también una cuenta ya eliminada) recibe el error genérico y suma en los dos contadores")
    void correoSinCuentaRecibeElErrorGenerico() {
        CredencialesIncorrectas error = assertThrows(CredencialesIncorrectas.class,
                () -> iniciarSesion.ejecutar("nadie@correo.com", CONTRASENA, IP));

        assertThat(error.getMessage()).isEqualTo(MENSAJE_GENERICO);
        assertThat(bloqueos.anotados).containsExactly(
                new Anotacion(sha256("CUENTA:nadie@correo.com"), TipoDeContador.CUENTA),
                new Anotacion(sha256("IP:" + IP), TipoDeContador.IP));
    }

    @Test
    @DisplayName("un correo sin cuenta compara la contraseña una vez contra el hash de relleno")
    void correoSinCuentaComparaContraElHashDeRelleno() {
        fallar("nadie@correo.com", IP);

        assertThat(hasheador.comparaciones)
                .containsExactly(new Comparacion(ERRONEA, hasheador.calculados.getFirst()));
    }

    @Test
    @DisplayName("el hash de relleno se calcula una sola vez, al crear el caso de uso")
    void elHashDeRellenoSeCalculaUnaSolaVez() {
        assertThat(hasheador.calculados).hasSize(1);

        fallar("nadie@correo.com", IP);
        fallar("otra@correo.com", IP);
        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(hasheador.calculados).hasSize(1);
    }

    // ---------- Fallos indistinguibles ----------

    @Test
    @DisplayName("contraseña errónea y correo sin cuenta dan el mismo tipo de error y el mismo mensaje")
    void losFallosTienenElMismoErrorYMensaje() {
        RuntimeException porContrasena = assertThrows(RuntimeException.class,
                () -> iniciarSesion.ejecutar(CORREO, ERRONEA, IP));
        RuntimeException porCorreo = assertThrows(RuntimeException.class,
                () -> iniciarSesion.ejecutar("nadie@correo.com", ERRONEA, IP));

        assertThat(porContrasena).isExactlyInstanceOf(CredencialesIncorrectas.class);
        assertThat(porCorreo).isExactlyInstanceOf(CredencialesIncorrectas.class);
        assertThat(porCorreo.getMessage()).isEqualTo(porContrasena.getMessage());
    }

    @Test
    @DisplayName("contraseña errónea y correo sin cuenta hacen las mismas llamadas en el mismo orden")
    void losFallosHacenLasMismasLlamadas() {
        fallar(CORREO, IP);
        List<String> porContrasena = List.copyOf(llamadas);
        llamadas.clear();

        fallar("nadie@correo.com", IP);
        List<String> porCorreo = List.copyOf(llamadas);

        assertThat(porContrasena).isEqualTo(LLAMADAS_DE_UN_FALLO);
        assertThat(porCorreo).isEqualTo(porContrasena);
    }

    // ---------- Estados que pueden entrar ----------

    /**
     * Recorre todos los estados que existen. Si mañana se añade uno (por ejemplo, una cuenta
     * suspendida), esta prueba fallará para él hasta que alguien decida si puede entrar.
     */
    @ParameterizedTest(name = "una cuenta en estado {0} puede iniciar sesión")
    @EnumSource(EstadoCuenta.class)
    void todosLosEstadosActualesPuedenEntrar(EstadoCuenta estado) {
        Usuario cuenta = cuentaCon(estado, "cuenta-" + estado.name().toLowerCase() + "@correo.com");
        usuarios.agregar(cuenta);

        SesionIniciada sesion = iniciarSesion.ejecutar(cuenta.correo().valor(), CONTRASENA, IP);

        assertThat(sesion.usuario()).isEqualTo(cuenta);
    }

    // ---------- Normalización del correo ----------

    @Test
    @DisplayName("el correo con mayúsculas y espacios entra en la misma cuenta")
    void elCorreoSeNormaliza() {
        SesionIniciada sesion = iniciarSesion.ejecutar("  Ana@Correo.COM ", CONTRASENA, IP);

        assertThat(sesion.usuario()).isEqualTo(ana);
    }

    @Test
    @DisplayName("los fallos con distintas formas del mismo correo suman en el mismo contador")
    void lasFormasDelMismoCorreoSumanJuntas() {
        fallar("ANA@correo.com", IP);
        fallar("ana@correo.com", IP);
        fallar(" Ana@Correo.Com", IP);
        fallar("ana@CORREO.com ", IP);
        fallar("aNa@correo.COM", IP);

        assertThrows(DemasiadosIntentos.class, () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));
    }

    // ---------- Bloqueo ----------

    @Test
    @DisplayName("tras 5 fallos, el sexto intento con la contraseña correcta se frena con 1 minuto de espera")
    void elSextoIntentoSeFrenaAunqueLaContrasenaSeaCorrecta() {
        fallarCincoVeces();

        DemasiadosIntentos error = assertThrows(DemasiadosIntentos.class,
                () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));

        assertThat(error.espera()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("un intento bloqueado no busca la cuenta, no compara la contraseña y no toca ningún contador")
    void unIntentoBloqueadoNoTocaNada() {
        fallarCincoVeces();
        int comparacionesPrevias = hasheador.comparaciones.size();
        int anotadosPrevios = bloqueos.anotados.size();
        Map<String, ControlDeIntentos> contadoresPrevios = Map.copyOf(bloqueos.contadores);
        llamadas.clear();

        assertThrows(DemasiadosIntentos.class, () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));

        // Solo se calcularon las dos huellas y se consultaron los dos contadores.
        assertThat(llamadas).containsExactly("huella", "huella", "buscarBloqueo", "buscarBloqueo");
        assertThat(hasheador.comparaciones).hasSize(comparacionesPrevias);
        assertThat(bloqueos.anotados).hasSize(anotadosPrevios);
        assertThat(bloqueos.olvidadas).isEmpty();
        assertThat(bloqueos.contadores).isEqualTo(contadoresPrevios);
        assertThat(emisor.emitidos).isEmpty();
        assertThat(renovaciones.guardados).isEmpty();
    }

    @Test
    @DisplayName("cuando termina la espera, la contraseña correcta vuelve a entrar")
    void alTerminarLaEsperaSePuedeEntrar() {
        fallarCincoVeces();

        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(iniciarSesion.ejecutar(CORREO, CONTRASENA, IP).usuario()).isEqualTo(ana);
    }

    // ---------- Cuenta frente a IP ----------

    @Test
    @DisplayName("una IP bloqueada frena el intento aunque la cuenta no tenga fallos")
    void laIpBloqueadaFrenaAunqueLaCuentaEsteLimpia() {
        for (int i = 1; i <= 20; i++) {
            fallar("persona" + i + "@correo.com", IP);
        }

        DemasiadosIntentos error = assertThrows(DemasiadosIntentos.class,
                () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));

        assertThat(error.espera()).isEqualTo(Duration.ofMinutes(1));
        assertThat(bloqueos.contadores).doesNotContainKey(sha256("CUENTA:" + CORREO));
    }

    @Test
    @DisplayName("una cuenta bloqueada frena el intento aunque llegue desde una IP sin fallos")
    void laCuentaBloqueadaFrenaDesdeUnaIpLimpia() {
        for (int i = 1; i <= 5; i++) {
            fallar(CORREO, "198.51.100." + i);
        }

        DemasiadosIntentos error = assertThrows(DemasiadosIntentos.class,
                () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));

        assertThat(error.espera()).isEqualTo(Duration.ofMinutes(1));
        assertThat(bloqueos.contadores).doesNotContainKey(sha256("IP:" + IP));
    }

    @Test
    @DisplayName("si la cuenta y la IP están bloqueadas, se informa la espera más larga")
    void conLosDosBloqueadosSeInformaLaEsperaMasLarga() {
        bloqueos.contadores.put(sha256("CUENTA:" + CORREO),
                new ControlDeIntentos(7, AHORA.minusSeconds(300), AHORA.plus(Duration.ofMinutes(4))));
        bloqueos.contadores.put(sha256("IP:" + IP),
                new ControlDeIntentos(20, AHORA.minusSeconds(300), AHORA.plus(Duration.ofMinutes(2))));

        DemasiadosIntentos error = assertThrows(DemasiadosIntentos.class,
                () -> iniciarSesion.ejecutar(CORREO, CONTRASENA, IP));

        assertThat(error.espera()).isEqualTo(Duration.ofMinutes(4));
    }

    // ---------- Huellas de los contadores ----------

    @Test
    @DisplayName("las huellas de los contadores se calculan sobre CUENTA:<correo normalizado> e IP:<ip>")
    void lasHuellasUsanElCorreoNormalizadoYLaIp() {
        fallar("  Ana@Correo.COM ", IP);

        assertThat(huellas.textos).containsExactly("CUENTA:" + CORREO, "IP:" + IP);
    }

    @Test
    @DisplayName("ni el correo ni la IP en claro llegan nunca al repositorio de bloqueos")
    void elRepositorioDeBloqueosSoloRecibeHuellas() {
        fallar(CORREO, IP);
        iniciarSesion.ejecutar(CORREO, CONTRASENA, IP);

        assertThat(bloqueos.clavesRecibidas).isNotEmpty().allSatisfy(clave -> {
            assertThat(clave).matches("[0-9a-f]{64}");
            assertThat(clave).doesNotContain(CORREO).doesNotContain(IP);
        });
    }

    // ---------- Ayudas ----------

    /** Intenta entrar con la contraseña errónea y comprueba que se rechaza como credenciales. */
    private void fallar(String correo, String ip) {
        assertThrows(CredencialesIncorrectas.class, () -> iniciarSesion.ejecutar(correo, ERRONEA, ip));
    }

    private void fallarCincoVeces() {
        for (int i = 0; i < 5; i++) {
            fallar(CORREO, IP);
        }
    }

    private static Usuario cuentaCon(EstadoCuenta estado) {
        return cuentaCon(estado, CORREO);
    }

    /** Cuenta cuya contraseña es {@link #CONTRASENA}, con el hash que produce el hasheador falso. */
    private static Usuario cuentaCon(EstadoCuenta estado, String correo) {
        return new Usuario(UUID.randomUUID(), "Ana", correo, new CorreoNormalizado(correo),
                HasheadorFalso.HASH_DE + CONTRASENA, Rol.USUARIO, estado);
    }

    /** SHA-256 en hexadecimal: la misma forma que tiene una huella real. */
    private static String sha256(String texto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Puertos falsos ----------

    /** Una comparación de contraseña: lo que escribió la persona y el hash contra el que se comparó. */
    private record Comparacion(String contrasena, String hash) {
    }

    /** Un fallo anotado en el repositorio de bloqueos. */
    private record Anotacion(String huellaClave, TipoDeContador tipo) {
    }

    /** Reloj detenido que solo avanza cuando la prueba lo pide. */
    private static final class RelojDePrueba extends Clock {

        private Instant ahora;

        RelojDePrueba(Instant ahora) {
            this.ahora = ahora;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    /** Cuentas guardadas en un mapa por correo normalizado. */
    private static final class UsuariosEnMemoria implements RepositorioUsuarios {

        private final List<String> llamadas;
        private final Map<CorreoNormalizado, Usuario> cuentas = new HashMap<>();

        UsuariosEnMemoria(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        void agregar(Usuario usuario) {
            cuentas.put(usuario.correo(), usuario);
        }

        @Override
        public boolean existeConCorreo(CorreoNormalizado correo) {
            throw new UnsupportedOperationException("El inicio de sesión no lo usa.");
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo) {
            llamadas.add("buscarCuenta");
            return Optional.ofNullable(cuentas.get(correo));
        }

        @Override
        public void guardar(Usuario usuario) {
            throw new UnsupportedOperationException("El inicio de sesión no lo usa.");
        }

        @Override
        public void marcarVerificada(UUID usuarioId, Instant ahora) {
            throw new UnsupportedOperationException("El inicio de sesión no lo usa.");
        }
    }

    /**
     * Contadores en un mapa por huella, con las reglas reales de {@link ControlDeIntentos}.
     * Apunta cada clave que recibe para poder comprobar que nunca llega un dato en claro.
     */
    private static final class BloqueosEnMemoria implements RepositorioBloqueos {

        private final List<String> llamadas;
        final Map<String, ControlDeIntentos> contadores = new HashMap<>();
        final List<String> clavesRecibidas = new ArrayList<>();
        final List<Anotacion> anotados = new ArrayList<>();
        final List<String> olvidadas = new ArrayList<>();

        BloqueosEnMemoria(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public Optional<ControlDeIntentos> buscar(String huellaClave) {
            llamadas.add("buscarBloqueo");
            clavesRecibidas.add(huellaClave);
            return Optional.ofNullable(contadores.get(huellaClave));
        }

        @Override
        public ControlDeIntentos anotarFallo(String huellaClave, TipoDeContador tipo, Instant ahora) {
            llamadas.add("anotarFallo:" + tipo);
            clavesRecibidas.add(huellaClave);
            anotados.add(new Anotacion(huellaClave, tipo));
            ControlDeIntentos previo = contadores.get(huellaClave);
            ControlDeIntentos nuevo = previo == null
                    ? ControlDeIntentos.trasPrimerFallo(ahora, tipo.limite())
                    : previo.trasOtroFallo(ahora, tipo.limite());
            contadores.put(huellaClave, nuevo);
            return nuevo;
        }

        @Override
        public void olvidar(String huellaClave) {
            llamadas.add("olvidar");
            clavesRecibidas.add(huellaClave);
            olvidadas.add(huellaClave);
            contadores.remove(huellaClave);
        }
    }

    /** Tokens de renovación guardados en una lista. */
    private static final class TokensRenovacionEnMemoria implements RepositorioTokensRenovacion {

        private final List<String> llamadas;
        final List<TokenRenovacion> guardados = new ArrayList<>();

        TokensRenovacionEnMemoria(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public void guardar(TokenRenovacion token) {
            llamadas.add("guardarRenovacion");
            guardados.add(token);
        }
    }

    /** Emisor que no firma nada: devuelve un texto numerado y apunta para quién y a qué hora. */
    private static final class EmisorFalso implements EmisorTokenAcceso {

        private final List<String> llamadas;
        final List<Usuario> cuentas = new ArrayList<>();
        final List<Instant> horas = new ArrayList<>();
        final List<TokenAcceso> emitidos = new ArrayList<>();

        EmisorFalso(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public TokenAcceso emitir(Usuario usuario, Instant ahora) {
            llamadas.add("emitir");
            cuentas.add(usuario);
            horas.add(ahora);
            TokenAcceso token = new TokenAcceso(
                    "token-de-acceso-" + (emitidos.size() + 1), ahora.plus(TokenAcceso.VIGENCIA));
            emitidos.add(token);
            return token;
        }
    }

    /**
     * Hasheador sin criptografía: el "hash" de una contraseña es el texto {@code hash-de:}
     * seguido de ella. Apunta cada hash que calcula y cada comparación que se le pide.
     */
    private static final class HasheadorFalso implements HasheadorContrasena {

        static final String HASH_DE = "hash-de:";

        private final List<String> llamadas;
        final List<String> calculados = new ArrayList<>();
        final List<Comparacion> comparaciones = new ArrayList<>();

        HasheadorFalso(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public String hashear(String contrasena) {
            llamadas.add("hashear");
            String hash = HASH_DE + contrasena;
            calculados.add(hash);
            return hash;
        }

        @Override
        public boolean coincide(String contrasena, String hash) {
            llamadas.add("comparar");
            comparaciones.add(new Comparacion(contrasena, hash));
            return hash.equals(HASH_DE + contrasena);
        }
    }

    /** Generador que devuelve valores numerados y recuerda el último. */
    private static final class GeneradorFalso implements GeneradorTokens {

        private int generados;
        String ultimo;

        @Override
        public String generar() {
            generados++;
            ultimo = "valor-aleatorio-" + generados;
            return ultimo;
        }
    }

    /** Una consulta de políticas: de qué cuenta y a qué hora. */
    private record ConsultaDePolitica(UUID usuarioId, Instant ahora) {
    }

    /** Consulta de políticas cuya respuesta fija la prueba; apunta cada consulta. */
    private static final class PoliticasFalsas implements ConsultaDePoliticas {

        private final List<String> llamadas;
        final List<ConsultaDePolitica> consultas = new ArrayList<>();
        boolean pendiente;

        PoliticasFalsas(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public boolean hayPoliticaPendiente(UUID usuarioId, Instant ahora) {
            llamadas.add("consultarPoliticas");
            consultas.add(new ConsultaDePolitica(usuarioId, ahora));
            return pendiente;
        }
    }

    /** Calculadora que apunta cada texto recibido y devuelve su SHA-256 real. */
    private static final class HuellasFalsas implements CalculadorHuellas {

        private final List<String> llamadas;
        final List<String> textos = new ArrayList<>();

        HuellasFalsas(List<String> llamadas) {
            this.llamadas = llamadas;
        }

        @Override
        public String huellaDe(String texto) {
            llamadas.add("huella");
            textos.add(texto);
            return sha256(texto);
        }
    }
}