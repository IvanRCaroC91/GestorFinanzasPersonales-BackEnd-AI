package com.smartkash.identidad.registro.aplicacion;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.registro.dominio.ErrorDeRegistro;
import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.registro.dominio.PoliticaContrasena;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import com.smartkash.identidad.registro.puertos.RepositorioPoliticas;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del caso de uso {@link RegistrarUsuario}.
 *
 * <p>Qué comprueban: que un registro correcto guarda la cuenta y la prueba de
 * autorización; que una contraseña débil, una política sin aceptar o una versión que no
 * es la vigente se rechazan sin guardar nada; y que un correo ya registrado se trata
 * exactamente igual que uno nuevo visto desde fuera (sin error y calculando el hash),
 * pero sin crear una segunda cuenta.</p>
 *
 * <p>Cómo lo hacen: los puertos se sustituyen por versiones falsas que guardan en listas
 * en memoria, definidas al final de este archivo. Así no hace falta Spring ni una base
 * de datos y las pruebas son rápidas.</p>
 */
class RegistrarUsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-01-15T10:30:00Z");
    private static final String VERSION_VIGENTE = "1.0";

    /** Contraseña inventada para las pruebas; cumple todas las reglas. */
    private static final String CONTRASENA_VALIDA = "una-clave-de-prueba-123";

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final AutorizacionesEnMemoria autorizaciones = new AutorizacionesEnMemoria();
    private final PoliticasFalsas politicas = new PoliticasFalsas();
    private final HasheadorFalso hasheador = new HasheadorFalso();

    private final RegistrarUsuario registrarUsuario = new RegistrarUsuario(
            usuarios,
            autorizaciones,
            politicas,
            hasheador,
            new PoliticaContrasena(contrasena -> false),
            Clock.fixed(AHORA, ZoneOffset.UTC));

    // ---------- Registro correcto ----------

    @Test
    @DisplayName("un registro correcto guarda la cuenta con los datos que decide el servidor")
    void registroCorrectoGuardaLaCuenta() {
        registrarUsuario.ejecutar(datos("  Ana  ", " Ana@Correo.COM ", CONTRASENA_VALIDA));

        assertEquals(1, usuarios.guardados.size());
        Usuario usuario = usuarios.guardados.getFirst();
        assertEquals("Ana", usuario.nombre());
        assertEquals("Ana@Correo.COM", usuario.correoOriginal());
        assertEquals(new CorreoNormalizado("ana@correo.com"), usuario.correo());
        assertEquals(Rol.USUARIO, usuario.rol());
        assertEquals(EstadoCuenta.PENDIENTE_VERIFICACION, usuario.estado());
    }

    @Test
    @DisplayName("guarda el hash que devuelve el hasheador, nunca la contraseña")
    void guardaElHashYNoLaContrasena() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));

        Usuario usuario = usuarios.guardados.getFirst();
        assertEquals("hash-falso-1", usuario.hashContrasena());
        assertNotEquals(CONTRASENA_VALIDA, usuario.hashContrasena());
        assertEquals(List.of(CONTRASENA_VALIDA), hasheador.recibidas);
    }

    @Test
    @DisplayName("guarda la prueba de autorización con la versión, el momento y el canal")
    void guardaLaPruebaDeAutorizacion() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));

        assertEquals(1, autorizaciones.guardadas.size());
        Autorizacion autorizacion = autorizaciones.guardadas.getFirst();
        assertEquals(usuarios.guardados.getFirst().id(), autorizacion.usuarioId());
        assertEquals(VERSION_VIGENTE, autorizacion.versionPolitica());
        assertTrue(autorizacion.otorgada());
        assertEquals("WEB", autorizacion.canal());
        assertEquals(AHORA, autorizacion.registradaEn());
    }

    // ---------- Rechazos ----------

    @Test
    @DisplayName("una contraseña de 11 caracteres se rechaza y no se guarda nada")
    void contrasenaDebil() {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", "a".repeat(11))));

        assertEquals(Motivo.CONTRASENA_MUY_CORTA, error.motivo());
        assertNadaGuardado();
    }

    @Test
    @DisplayName("una contraseña igual al correo se rechaza aunque cambien las mayúsculas")
    void contrasenaIgualAlCorreo() {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", "ANA@correo.com")));

        assertEquals(Motivo.CONTRASENA_IGUAL_AL_CORREO, error.motivo());
        assertNadaGuardado();
    }

    @Test
    @DisplayName("si no se acepta la política, se rechaza indicando que es obligatoria")
    void politicaSinAceptar() {
        DatosDeRegistro sinAceptar = new DatosDeRegistro(
                "Ana", "ana@correo.com", CONTRASENA_VALIDA, false, VERSION_VIGENTE);

        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(sinAceptar));

        assertEquals(Motivo.POLITICA_NO_ACEPTADA, error.motivo());
        assertEquals("aceptaPolitica", error.campo());
        assertNadaGuardado();
    }

    @Test
    @DisplayName("si la versión enviada no es la vigente, se rechaza")
    void versionQueNoEsLaVigente() {
        DatosDeRegistro versionVieja = new DatosDeRegistro(
                "Ana", "ana@correo.com", CONTRASENA_VALIDA, true, "0.9");

        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(versionVieja));

        assertEquals(Motivo.VERSION_POLITICA_NO_VIGENTE, error.motivo());
        assertEquals("versionPolitica", error.campo());
        assertNadaGuardado();
    }

    @Test
    @DisplayName("si no se envía la versión de la política, se rechaza")
    void versionAusente() {
        DatosDeRegistro sinVersion = new DatosDeRegistro(
                "Ana", "ana@correo.com", CONTRASENA_VALIDA, true, null);

        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(sinVersion));

        assertEquals(Motivo.VERSION_POLITICA_NO_VIGENTE, error.motivo());
        assertNadaGuardado();
    }

    @Test
    @DisplayName("si no hay ninguna política publicada, falla como error del sistema")
    void sinPoliticaPublicada() {
        politicas.vigente = Optional.empty();

        // No es culpa de la persona: falta un dato inicial del sistema.
        assertThrows(IllegalStateException.class,
                () -> registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA)));
        assertNadaGuardado();
    }

    // ---------- Correo ya registrado ----------

    @Test
    @DisplayName("un correo ya registrado no da error ni crea una segunda cuenta")
    void correoYaRegistrado() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));

        assertDoesNotThrow(() ->
                registrarUsuario.ejecutar(datos("Otra", "ANA@Correo.com", CONTRASENA_VALIDA)));

        assertEquals(1, usuarios.guardados.size());
        assertEquals("Ana", usuarios.guardados.getFirst().nombre());
        assertEquals(1, autorizaciones.guardadas.size());
    }

    @Test
    @DisplayName("con un correo ya registrado también se calcula el hash")
    void correoYaRegistradoCalculaElHash() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));

        registrarUsuario.ejecutar(datos("Otra", "ana@correo.com", CONTRASENA_VALIDA));

        // Dos solicitudes, dos cálculos: así la respuesta tarda parecido en ambos casos
        // y el tiempo no delata que el correo ya existía.
        assertEquals(2, hasheador.recibidas.size());
    }

    @Test
    @DisplayName("si otra solicitud gana la carrera al guardar, se responde igual y sin autorización")
    void carreraAlGuardar() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));
        // Simula dos solicitudes casi simultáneas: la consulta dice que el correo está
        // libre, pero al guardar la base de datos detecta el duplicado.
        usuarios.consultaDiceQueNoExiste = true;

        assertDoesNotThrow(() ->
                registrarUsuario.ejecutar(datos("Otra", "ana@correo.com", CONTRASENA_VALIDA)));

        assertEquals(1, usuarios.guardados.size());
        assertEquals(1, autorizaciones.guardadas.size());
        assertEquals(2, hasheador.recibidas.size());
    }

    @Test
    @DisplayName("los datos inválidos dan el mismo error exista o no el correo")
    void losErroresNoDelatanSiElCorreoExiste() {
        registrarUsuario.ejecutar(datos("Ana", "ana@correo.com", CONTRASENA_VALIDA));

        ErrorDeRegistro conCorreoExistente = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(datos("   ", "ana@correo.com", CONTRASENA_VALIDA)));
        ErrorDeRegistro conCorreoNuevo = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(datos("   ", "nuevo@correo.com", CONTRASENA_VALIDA)));

        assertEquals(Motivo.NOMBRE_OBLIGATORIO, conCorreoExistente.motivo());
        assertEquals(conCorreoNuevo.motivo(), conCorreoExistente.motivo());
    }

    // ---------- Nombre ----------

    @Test
    @DisplayName("un nombre vacío se rechaza con el error del registro, su motivo y su campo")
    void rechazaNombreVacioConElErrorDelRegistro() {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(datos("   ", "ana@correo.com", CONTRASENA_VALIDA)));

        assertEquals(Motivo.NOMBRE_OBLIGATORIO, error.motivo());
        assertEquals("nombre", error.campo());
        assertEquals("El nombre es obligatorio.", error.getMessage());
        assertEquals(0, usuarios.guardados.size());
    }

    @Test
    @DisplayName("un nombre de 101 caracteres se rechaza con el error del registro, su motivo y su campo")
    void rechazaNombreMuyLargoConElErrorDelRegistro() {
        ErrorDeRegistro error = assertThrows(ErrorDeRegistro.class,
                () -> registrarUsuario.ejecutar(
                        datos("a".repeat(101), "ana@correo.com", CONTRASENA_VALIDA)));

        assertEquals(Motivo.NOMBRE_MUY_LARGO, error.motivo());
        assertEquals("nombre", error.campo());
        assertEquals("El nombre no puede tener más de 100 caracteres.", error.getMessage());
        assertEquals(0, usuarios.guardados.size());
    }

    // ---------- Datos de entrada ----------

    @Test
    @DisplayName("el texto de depuración de los datos de entrada no muestra la contraseña")
    void losDatosDeEntradaNoRevelanLaContrasena() {
        String texto = datos("Ana", "ana@correo.com", CONTRASENA_VALIDA).toString();

        assertFalse(texto.contains(CONTRASENA_VALIDA));
    }

    // ---------- Ayudas ----------

    /** Datos de registro con la política aceptada en su versión vigente. */
    private static DatosDeRegistro datos(String nombre, String correo, String contrasena) {
        return new DatosDeRegistro(nombre, correo, contrasena, true, VERSION_VIGENTE);
    }

    private void assertNadaGuardado() {
        assertTrue(usuarios.guardados.isEmpty());
        assertTrue(autorizaciones.guardadas.isEmpty());
    }

    // ---------- Puertos falsos ----------

    /** Repositorio de usuarios que guarda en una lista y rechaza correos duplicados. */
    private static final class UsuariosEnMemoria implements RepositorioUsuarios {

        final List<Usuario> guardados = new ArrayList<>();

        /** Si es {@code true}, la consulta responde "no existe" aunque el correo esté guardado. */
        boolean consultaDiceQueNoExiste;

        @Override
        public boolean existeConCorreo(CorreoNormalizado correo) {
            return !consultaDiceQueNoExiste && yaGuardado(correo);
        }

        @Override
        public Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo) {
            return guardados.stream().filter(usuario -> usuario.correo().equals(correo)).findFirst();
        }

        @Override
        public void guardar(Usuario usuario) {
            if (yaGuardado(usuario.correo())) {
                throw new CorreoYaRegistrado();
            }
            guardados.add(usuario);
        }

        @Override
        public void marcarVerificada(java.util.UUID usuarioId, Instant ahora) {
            throw new UnsupportedOperationException("El registro no verifica cuentas.");
        }

        private boolean yaGuardado(CorreoNormalizado correo) {
            return guardados.stream().anyMatch(usuario -> usuario.correo().equals(correo));
        }
    }

    /** Repositorio de autorizaciones que guarda en una lista. */
    private static final class AutorizacionesEnMemoria implements RepositorioAutorizaciones {

        final List<Autorizacion> guardadas = new ArrayList<>();

        @Override
        public void guardar(Autorizacion autorizacion) {
            guardadas.add(autorizacion);
        }
    }

    /** Repositorio de políticas cuya versión vigente puede cambiarse desde la prueba. */
    private static final class PoliticasFalsas implements RepositorioPoliticas {

        Optional<String> vigente = Optional.of(VERSION_VIGENTE);

        @Override
        public Optional<String> versionVigente() {
            return vigente;
        }
    }

    /** Hasheador que anota lo que recibe y devuelve un texto numerado en lugar de un hash real. */
    private static final class HasheadorFalso implements HasheadorContrasena {

        final List<String> recibidas = new ArrayList<>();

        @Override
        public String hashear(String contrasena) {
            recibidas.add(contrasena);
            return "hash-falso-" + recibidas.size();
        }

        @Override
        public boolean coincide(String contrasena, String hash) {
            throw new UnsupportedOperationException("El registro no compara contraseñas.");
        }
    }
}
