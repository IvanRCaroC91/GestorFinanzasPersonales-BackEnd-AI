package com.smartkash.identidad.cuenta.puertos;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.cuenta.dominio.Usuario;

/**
 * Lugar donde se guardan y se consultan las cuentas de usuario.
 *
 * <p>Qué es: un puerto. El caso de uso del registro solo sabe que puede preguntar si un
 * correo existe y pedir que se guarde una cuenta; no sabe que detrás hay una base de
 * datos. La clase que implemente esta interfaz hará el trabajo real contra la tabla de
 * usuarios.</p>
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
}
