package com.smartkash.identidad.cuenta.puertos;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.cuenta.dominio.Usuario;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Lugar donde se guardan y se consultan las cuentas de usuario.
 *
 * <p>Qué es: un puerto. Los casos de uso solo saben que pueden preguntar si un correo
 * existe, buscar una cuenta y pedir que se guarde una nueva; no saben que detrás hay una
 * base de datos. La clase que implemente esta interfaz hará el trabajo real contra la
 * tabla de usuarios.</p>
 */
public interface RepositorioUsuarios {

    /**
     * Indica si ya hay una cuenta con ese correo.
     *
     * @param correo el correo ya normalizado; así no importan las mayúsculas con que se escribió
     * @return {@code true} si existe una cuenta con ese correo
     */
    boolean existeConCorreo(CorreoNormalizado correo);

    /**
     * Busca la cuenta registrada con ese correo y la devuelve completa, incluido el hash de
     * su contraseña.
     *
     * <p>Para qué sirve: el inicio de sesión necesita el hash guardado para compararlo con
     * la contraseña que escribe la persona, y el rol y el estado para decidir si puede
     * entrar.</p>
     *
     * <p>Devuelve un {@link Optional}: una caja que puede venir con una cuenta dentro o
     * vacía. Obliga a quien llama a decidir qué hacer cuando no hay cuenta, en lugar de
     * recibir un {@code null} que se le pueda olvidar comprobar.</p>
     *
     * @param correo el correo ya normalizado; así no importan las mayúsculas con que se escribió
     * @return la cuenta, o una caja vacía si nadie se registró con ese correo
     */
    Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo);

    /**
     * Guarda una cuenta nueva.
     *
     * <p>Reglas: aunque antes se haya consultado {@link #existeConCorreo}, otra
     * solicitud puede haber registrado el mismo correo un instante después. En ese caso
     * la base de datos rechaza el duplicado y este método lo informa con
     * {@link CorreoYaRegistrado}.</p>
     *
     * @param usuario la cuenta a guardar
     * @throws CorreoYaRegistrado si ya existe una cuenta con el mismo correo
     */
    void guardar(Usuario usuario);

    /**
     * Anota que la persona confirmó su correo.
     *
     * <p>Reglas: una cuenta pendiente de verificación pasa a activa; una en otro estado (por
     * ejemplo, en periodo de gracia de eliminación) conserva su estado. La fecha de verificación
     * se anota solo la primera vez. Si la cuenta no existe, no hace nada.</p>
     *
     * @param usuarioId la cuenta
     * @param ahora     momento de la verificación
     */
    void marcarVerificada(UUID usuarioId, Instant ahora);
}
