package com.smartkash.identidad.sesion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Caso de uso: dejar entrar a una persona que demuestra quién es con su correo y su
 * contraseña.
 *
 * <p>Qué hace: coordina los pasos del inicio de sesión en un orden fijo. Las reglas del
 * bloqueo viven en el dominio y el cálculo de hashes, huellas y firmas en los puertos; aquí
 * solo se decide qué se hace y cuándo.</p>
 *
 * <p>De dónde vienen los datos: la capa web entrega el correo y la contraseña del cuerpo de
 * la solicitud y la dirección IP de la conexión. A dónde van: los contadores de fallos y el
 * token de renovación se guardan a través de los repositorios; los tokens y la cuenta
 * vuelven a la capa web dentro de {@link SesionIniciada}.</p>
 *
 * <p>Orden de los pasos (cada uno tiene su porqué):</p>
 * <ol>
 *   <li>Normalizar el correo, para que {@code Ana@Correo.com} y {@code ana@correo.com}
 *       cuenten como la misma cuenta.</li>
 *   <li>Calcular las huellas de {@code CUENTA:<correo>} y de {@code IP:<dirección>}. Los
 *       contadores se identifican por esas huellas, nunca por el dato en claro.</li>
 *   <li>Mirar los dos contadores. Si alguno está bloqueado, se lanza
 *       {@link DemasiadosIntentos} sin buscar la cuenta, sin comparar la contraseña y sin
 *       anotar nada: un intento durante el bloqueo no cuenta.</li>
 *   <li>Buscar la cuenta.</li>
 *   <li>Comparar la contraseña: con el hash de la cuenta si existe, o con el hash de
 *       relleno si no existe.</li>
 *   <li>Si algo falla, anotar un fallo en la cuenta y otro en la IP y lanzar
 *       {@link CredencialesIncorrectas}. Si todo va bien, borrar el contador de la cuenta y
 *       emitir los dos tokens.</li>
 * </ol>
 *
 * <p><b>Qué es el hash de relleno y para qué sirve.</b> Comparar una contraseña con Argon2
 * tarda a propósito (decenas de milisegundos). Si cuando el correo no existe se respondiera
 * de inmediato, sin comparar nada, un atacante podría cronometrar las respuestas: las
 * rápidas serían correos sin cuenta y las lentas, correos registrados. Así averiguaría quién
 * usa la aplicación sin saber ninguna contraseña. Para evitarlo, cuando no hay cuenta se
 * compara igualmente la contraseña contra un hash de mentira: el hash de un valor aleatorio
 * que nadie conoce, calculado una sola vez al crear este caso de uso con los mismos
 * parámetros que los hashes reales. La comparación tarda lo mismo y falla siempre. Es como
 * un cajero que, aunque la tarjeta no exista, hace como que comprueba el PIN durante el
 * mismo tiempo antes de rechazarla.</p>
 *
 * <p><b>Regla clave, no revelar nada.</b> Contraseña errónea, correo sin cuenta y cuenta
 * que no puede entrar terminan igual: mismo error, mismo mensaje, mismas llamadas en el
 * mismo orden y una comparación de contraseña en todos los casos.</p>
 *
 * <p>Errores: {@link DemasiadosIntentos} si hay un bloqueo en curso;
 * {@link CredencialesIncorrectas} si no se puede dejar entrar a la persona;
 * {@link IllegalArgumentException} si el correo está vacío o es demasiado largo (la capa web
 * lo valida antes).</p>
 *
 * <p>Esta clase no usa Spring: se crea con {@code new} en las pruebas y, en la aplicación,
 * desde una clase de configuración de los adaptadores.</p>
 */
public class IniciarSesion {

    private static final String PREFIJO_CUENTA = "CUENTA:";
    private static final String PREFIJO_IP = "IP:";

    private final RepositorioUsuarios usuarios;
    private final RepositorioBloqueos bloqueos;
    private final RepositorioTokensRenovacion tokensRenovacion;
    private final EmisorTokenAcceso emisorTokenAcceso;
    private final HasheadorContrasena hasheador;
    private final GeneradorTokens generador;
    private final CalculadorHuellas huellas;
    private final ConsultaDePoliticas politicas;
    private final Clock reloj;

    /** Hash de un valor aleatorio que nadie conoce; ver la explicación de la clase. */
    private final String hashDeRelleno;

    /**
     * Crea el caso de uso con todo lo que necesita del exterior y calcula el hash de relleno.
     *
     * <p>El hash de relleno se calcula aquí, una sola vez, con el mismo hasheador que los
     * hashes reales. Parte de un valor aleatorio que se descarta enseguida, así que no hay
     * ninguna contraseña escrita en el código.</p>
     *
     * @param usuarios          dónde se buscan las cuentas
     * @param bloqueos          dónde se consultan y anotan los fallos
     * @param tokensRenovacion  dónde se guarda la huella del token de renovación
     * @param emisorTokenAcceso quién firma el token de acceso
     * @param hasheador         quién compara la contraseña con su hash
     * @param generador         quién produce los valores aleatorios
     * @param huellas           quién calcula las huellas de las claves y de los tokens
     * @param politicas         quién dice si la cuenta tiene una política pendiente de aceptar
     * @param reloj             de dónde se toma la hora; en las pruebas, un reloj controlado
     * @throws NullPointerException si falta alguno
     */
    public IniciarSesion(
            RepositorioUsuarios usuarios,
            RepositorioBloqueos bloqueos,
            RepositorioTokensRenovacion tokensRenovacion,
            EmisorTokenAcceso emisorTokenAcceso,
            HasheadorContrasena hasheador,
            GeneradorTokens generador,
            CalculadorHuellas huellas,
            ConsultaDePoliticas politicas,
            Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios);
        this.bloqueos = Objects.requireNonNull(bloqueos);
        this.tokensRenovacion = Objects.requireNonNull(tokensRenovacion);
        this.emisorTokenAcceso = Objects.requireNonNull(emisorTokenAcceso);
        this.hasheador = Objects.requireNonNull(hasheador);
        this.generador = Objects.requireNonNull(generador);
        this.huellas = Objects.requireNonNull(huellas);
        this.politicas = Objects.requireNonNull(politicas);
        this.reloj = Objects.requireNonNull(reloj);
        this.hashDeRelleno = hasheador.hashear(generador.generar());
    }

    /**
     * Inicia la sesión de una persona.
     *
     * @param correo     el correo tal como lo escribió la persona
     * @param contrasena la contraseña en claro, tal como la escribió
     * @param ip         la dirección IP de la conexión directa (nunca la de una cabecera
     *                   que pueda inventar el cliente)
     * @return los dos tokens y la cuenta
     * @throws DemasiadosIntentos       si la cuenta o la IP tienen un bloqueo en curso
     * @throws CredencialesIncorrectas  si no se puede dejar entrar a la persona
     * @throws IllegalArgumentException si el correo está vacío o es demasiado largo
     * @throws NullPointerException     si falta la contraseña o la IP
     */
    public SesionIniciada ejecutar(String correo, String contrasena, String ip) {
        Objects.requireNonNull(contrasena, "La contraseña es obligatoria.");
        Objects.requireNonNull(ip, "La dirección IP es obligatoria.");
        Instant ahora = reloj.instant();

        CorreoNormalizado correoNormalizado = new CorreoNormalizado(correo);
        String claveCuenta = huellas.huellaDe(PREFIJO_CUENTA + correoNormalizado.valor());
        String claveIp = huellas.huellaDe(PREFIJO_IP + ip);

        frenarSiHayBloqueo(claveCuenta, claveIp, ahora);

        Optional<Usuario> cuenta = usuarios.buscarPorCorreo(correoNormalizado);

        // Se compara siempre, haya cuenta o no, para que el tiempo de respuesta sea el mismo.
        String hashAComparar = cuenta.map(Usuario::hashContrasena).orElse(hashDeRelleno);
        boolean contrasenaCorrecta = hasheador.coincide(contrasena, hashAComparar);

        // La comprobación de la cuenta va después de comparar y en la misma condición: así
        // ningún motivo de rechazo se salta la comparación ni toma un camino distinto.
        if (!contrasenaCorrecta || cuenta.isEmpty() || !puedeIniciarSesion(cuenta.get())) {
            bloqueos.anotarFallo(claveCuenta, TipoDeContador.CUENTA, ahora);
            bloqueos.anotarFallo(claveIp, TipoDeContador.IP, ahora);
            throw new CredencialesIncorrectas();
        }

        Usuario usuario = cuenta.get();
        // Solo se borra el contador de la cuenta. El de la IP se conserva: acertar una
        // contraseña no demuestra que esa dirección no esté probando otras cuentas.
        bloqueos.olvidar(claveCuenta);
        return emitirTokens(usuario, ahora);
    }

    /*
     * Consulta los dos contadores antes de decidir. Si los dos están bloqueados, se informa
     * la espera más larga, que es la que de verdad hay que cumplir.
     */
    private void frenarSiHayBloqueo(String claveCuenta, String claveIp, Instant ahora) {
        Optional<Duration> esperaCuenta = bloqueos.buscar(claveCuenta)
                .flatMap(contador -> contador.esperaRestante(ahora));
        Optional<Duration> esperaIp = bloqueos.buscar(claveIp)
                .flatMap(contador -> contador.esperaRestante(ahora));

        Optional<Duration> espera = Stream.of(esperaCuenta, esperaIp)
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder());
        if (espera.isPresent()) {
            throw new DemasiadosIntentos(espera.get());
        }
    }

    /*
     * Decide qué estados de cuenta pueden entrar. El switch enumera todos los estados sin
     * rama por defecto: si mañana se añade uno nuevo (por ejemplo, una cuenta suspendida),
     * el código no compilará hasta que alguien decida aquí si puede entrar.
     *
     * Hoy pueden entrar los tres: la cuenta pendiente de verificar (para poder pedir otro
     * enlace) y la que está en plazo de eliminación (para poder cancelarla).
     */
    private static boolean puedeIniciarSesion(Usuario usuario) {
        return switch (usuario.estado()) {
            case ACTIVA, PENDIENTE_VERIFICACION, PENDIENTE_ELIMINACION -> true;
        };
    }

    /*
     * Firma el token de acceso y crea el de renovación. Del de renovación se guarda solo la
     * huella, en una familia nueva; el valor en claro sale únicamente en el resultado.
     */
    private SesionIniciada emitirTokens(Usuario usuario, Instant ahora) {
        TokenAcceso tokenAcceso = emisorTokenAcceso.emitir(usuario, ahora);

        String valorRenovacion = generador.generar();
        TokenRenovacion renovacion = TokenRenovacion.deSesionNueva(
                usuario.id(), huellas.huellaDe(valorRenovacion), ahora);
        tokensRenovacion.guardar(renovacion);

        // Solo se pregunta cuando la persona ya entró: así un fallo hace exactamente las mismas
        // llamadas sea cual sea su motivo.
        boolean requiereAceptarPolitica = politicas.hayPoliticaPendiente(usuario.id(), ahora);

        return new SesionIniciada(tokenAcceso, valorRenovacion, renovacion.expiraEn(),
                requiereAceptarPolitica, usuario);
    }
}