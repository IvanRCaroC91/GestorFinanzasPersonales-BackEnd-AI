package com.smartkash.identidad.registro.aplicacion;

import com.smartkash.identidad.registro.dominio.Autorizacion;
import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.cuenta.dominio.NombreNoValido;
import com.smartkash.identidad.registro.dominio.ErrorDeRegistro;
import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;
import com.smartkash.identidad.registro.dominio.PoliticaContrasena;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.registro.puertos.RepositorioAutorizaciones;
import com.smartkash.identidad.registro.puertos.RepositorioPoliticas;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/**
 * Caso de uso: crear la cuenta de una persona que se registra.
 *
 * <p>Qué hace: coordina los pasos del registro en orden. No contiene las reglas (esas
 * viven en el dominio) ni sabe cómo se guarda o se calcula un hash (eso lo hacen los
 * puertos); solo decide qué se hace y cuándo.</p>
 *
 * <p>De dónde vienen los datos: de {@link DatosDeRegistro}, que la capa web llena con el
 * cuerpo de la solicitud. A dónde van: a la tabla de usuarios y a la de autorizaciones,
 * a través de los repositorios.</p>
 *
 * <p>Regla clave, no revelar si un correo ya existe: visto desde fuera, registrarse con
 * un correo nuevo y con uno ya registrado debe ser indistinguible. Por eso, cuando el
 * correo ya existe, este caso de uso termina sin error, igual que en un registro
 * correcto, y antes de comprobarlo ya ha hecho el mismo trabajo en ambos casos: las
 * mismas validaciones y el mismo cálculo del hash, que es el paso más lento.</p>
 *
 * <p>Errores: {@link ErrorDeRegistro} si un dato de la persona incumple una regla;
 * {@link IllegalStateException} si el sistema no tiene ninguna política publicada.</p>
 *
 * <p>{@code @Service} hace que Spring cree este objeto al arrancar y le entregue por el
 * constructor todo lo que necesita.</p>
 */
@Service
public class RegistrarUsuario {

    private static final String CAMPO_NOMBRE = "nombre";
    private static final String CAMPO_ACEPTA_POLITICA = "aceptaPolitica";
    private static final String CAMPO_VERSION_POLITICA = "versionPolitica";

    private final RepositorioUsuarios usuarios;
    private final RepositorioAutorizaciones autorizaciones;
    private final RepositorioPoliticas politicas;
    private final HasheadorContrasena hasheador;
    private final PoliticaContrasena politicaContrasena;
    private final Clock reloj;

    /**
     * Crea el caso de uso con todo lo que necesita del exterior.
     *
     * @param usuarios           dónde se consultan y guardan las cuentas
     * @param autorizaciones     dónde se guarda la prueba de autorización
     * @param politicas          dónde se consulta la versión vigente de la política
     * @param hasheador          quién calcula el hash de la contraseña
     * @param politicaContrasena reglas que debe cumplir la contraseña
     * @param reloj              de dónde se toma la fecha y hora; en las pruebas es un
     *                           reloj detenido en un instante fijo
     * @throws NullPointerException si falta alguno
     */
    public RegistrarUsuario(
            RepositorioUsuarios usuarios,
            RepositorioAutorizaciones autorizaciones,
            RepositorioPoliticas politicas,
            HasheadorContrasena hasheador,
            PoliticaContrasena politicaContrasena,
            Clock reloj) {
        this.usuarios = Objects.requireNonNull(usuarios);
        this.autorizaciones = Objects.requireNonNull(autorizaciones);
        this.politicas = Objects.requireNonNull(politicas);
        this.hasheador = Objects.requireNonNull(hasheador);
        this.politicaContrasena = Objects.requireNonNull(politicaContrasena);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * Registra a la persona, o no hace nada visible si su correo ya tenía cuenta.
     *
     * <p>No devuelve nada a propósito: así quien llama no tiene forma de saber, ni de
     * contar por descuido, si la cuenta se creó o si el correo ya existía.</p>
     *
     * <p>{@code @Transactional}: Spring abre una transacción al entrar en el método y la
     * confirma al salir. Si sale un error, la deshace. Así la cuenta y su prueba de
     * autorización se guardan las dos o ninguna.</p>
     *
     * @param datos lo que envió la persona
     * @throws ErrorDeRegistro          si la contraseña o el nombre no cumplen sus reglas,
     *                                  si no se aceptó la política o si la versión enviada
     *                                  no es la vigente
     * @throws IllegalArgumentException si el correo está vacío o es demasiado largo
     * @throws IllegalStateException    si no hay ninguna política publicada
     */
    @Transactional
    public void ejecutar(DatosDeRegistro datos) {
        Objects.requireNonNull(datos, "Los datos de registro son obligatorios.");

        CorreoNormalizado correo = new CorreoNormalizado(datos.correo());
        politicaContrasena.validar(datos.contrasena(), correo);
        String versionVigente = exigirPoliticaVigenteAceptada(datos);

        // El hash y la cuenta se preparan ANTES de mirar si el correo existe. Así un
        // correo nuevo y uno repetido pasan por las mismas validaciones y tardan
        // parecido, y ni los errores ni el tiempo de respuesta delatan cuál es cuál.
        String hash = hasheador.hashear(datos.contrasena());
        Usuario usuario = crearCuenta(datos, correo, hash);

        if (usuarios.existeConCorreo(correo)) {
            return;
        }

        try {
            usuarios.guardar(usuario);
        } catch (CorreoYaRegistrado yaExiste) {
            // Otra solicitud con el mismo correo se guardó entre la consulta y este
            // guardado. Se trata igual que un correo ya registrado: sin error y sin
            // crear la prueba de autorización.
            return;
        }

        autorizaciones.guardar(
                Autorizacion.aceptacionDePolitica(usuario.id(), versionVigente, reloj.instant()));
    }

    /**
     * Crea la cuenta y, si el nombre no es válido, lo informa con el error del registro.
     *
     * <p>Las reglas del nombre viven en {@link Usuario}, que avisa con un error propio de
     * las cuentas ({@link NombreNoValido}) y no sabe nada del registro. Aquí ese aviso se
     * traduce a {@link ErrorDeRegistro}, que añade lo que solo el registro conoce: en qué
     * campo de la solicitud venía el nombre. El mensaje se conserva tal cual.</p>
     *
     * @return la cuenta nueva, aún sin guardar
     * @throws ErrorDeRegistro si el nombre falta o es demasiado largo
     */
    private static Usuario crearCuenta(DatosDeRegistro datos, CorreoNormalizado correo, String hash) {
        try {
            return Usuario.nuevo(datos.nombre(), datos.correo(), correo, hash);
        } catch (NombreNoValido nombreNoValido) {
            // El switch sin "default" obliga a decidir aquí qué hacer si algún día se añade
            // otra regla del nombre: mientras no se decida, el código no compila.
            Motivo motivo = switch (nombreNoValido.motivo()) {
                case OBLIGATORIO -> Motivo.NOMBRE_OBLIGATORIO;
                case MUY_LARGO -> Motivo.NOMBRE_MUY_LARGO;
            };
            throw new ErrorDeRegistro(motivo, CAMPO_NOMBRE, nombreNoValido.getMessage());
        }
    }

    /**
     * Comprueba que la persona aceptó la política y que la versión que vio es la vigente.
     *
     * @return la versión vigente, que es la que quedará en la prueba de autorización
     */
    private String exigirPoliticaVigenteAceptada(DatosDeRegistro datos) {
        if (!datos.aceptaPolitica()) {
            throw new ErrorDeRegistro(Motivo.POLITICA_NO_ACEPTADA, CAMPO_ACEPTA_POLITICA,
                    "Para registrarte debes aceptar la política de tratamiento de datos.");
        }

        String versionVigente = politicas.versionVigente().orElseThrow(() ->
                new IllegalStateException("No hay ninguna política de tratamiento publicada."));

        if (!versionVigente.equals(datos.versionPolitica())) {
            throw new ErrorDeRegistro(Motivo.VERSION_POLITICA_NO_VIGENTE, CAMPO_VERSION_POLITICA,
                    "La versión de la política enviada no es la vigente. Revisa la política actual.");
        }
        return versionVigente;
    }
}
