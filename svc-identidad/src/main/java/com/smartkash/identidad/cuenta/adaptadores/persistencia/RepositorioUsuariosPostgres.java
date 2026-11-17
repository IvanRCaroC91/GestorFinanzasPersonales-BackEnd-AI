package com.smartkash.identidad.cuenta.adaptadores.persistencia;

import com.smartkash.identidad.cuenta.dominio.CorreoNormalizado;
import com.smartkash.identidad.cuenta.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.cuenta.dominio.EstadoCuenta;
import com.smartkash.identidad.cuenta.dominio.Rol;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Guarda y consulta las cuentas de usuario en PostgreSQL.
 *
 * <p>Qué es: un adaptador. Implementa el puerto {@link RepositorioUsuarios}, que es lo único
 * que conoce el caso de uso, y traduce entre la clase de negocio {@link Usuario} y la tabla
 * de usuarios.</p>
 *
 * <p>{@code @Repository} convierte la clase en un bean: un objeto que Spring crea una sola
 * vez al arrancar y entrega a quien lo pida por su tipo. Por ejemplo, quien declare que
 * necesita un {@code RepositorioUsuarios} recibirá este objeto.</p>
 *
 * <p>De dónde vienen los datos: de los casos de uso (registro e inicio de sesión) y de la tabla
 * de usuarios. A dónde van: las cuentas nuevas, a la tabla; las leídas, al caso de uso que las
 * pidió.</p>
 */
@Repository
class RepositorioUsuariosPostgres implements RepositorioUsuarios {

    private final UsuariosJpa tabla;

    /**
     * @param tabla consultas sobre la tabla de usuarios; la entrega Spring
     */
    RepositorioUsuariosPostgres(UsuariosJpa tabla) {
        this.tabla = tabla;
    }

    /**
     * Indica si ya hay una cuenta con ese correo.
     *
     * @param correo el correo ya normalizado
     * @return {@code true} si existe una cuenta con ese correo
     */
    @Override
    public boolean existeConCorreo(CorreoNormalizado correo) {
        return tabla.existsByEmailNormalizado(correo.valor());
    }

    /**
     * Busca la cuenta registrada con ese correo.
     *
     * <p>Lee la fila y la convierte en la clase de negocio. El rol y el estado llegan de la
     * tabla como texto y aquí pasan a ser los tipos {@link Rol} y {@link EstadoCuenta}.</p>
     *
     * <p>Error: si la tabla tuviera un rol o un estado que el código no conoce, la
     * conversión lanza {@link IllegalArgumentException}. Es preferible fallar a tratar la
     * cuenta como si estuviera en un estado que no es el suyo.</p>
     *
     * @param correo el correo ya normalizado
     * @return la cuenta, o una caja vacía si nadie se registró con ese correo
     */
    @Override
    public Optional<Usuario> buscarPorCorreo(CorreoNormalizado correo) {
        return tabla.findByEmailNormalizado(correo.valor()).map(RepositorioUsuariosPostgres::aUsuario);
    }

    /* Convierte una fila leída en la clase de negocio. */
    private static Usuario aUsuario(UsuarioEntidad fila) {
        return new Usuario(
                fila.id(),
                fila.nombre(),
                fila.email(),
                new CorreoNormalizado(fila.emailNormalizado()),
                fila.hashContrasena(),
                Rol.valueOf(fila.rol()),
                EstadoCuenta.valueOf(fila.estado()),
                fila.eliminacionProgramadaEn());
    }

    /**
     * Guarda una cuenta nueva.
     *
     * <p>Regla: si el correo ya está registrado, no se inserta nada y se avisa con
     * {@link CorreoYaRegistrado}. La transacción en curso, si la hay, sigue siendo válida.</p>
     *
     * @param usuario la cuenta a guardar
     * @throws CorreoYaRegistrado si ya existe una cuenta con el mismo correo
     */
    @Override
    public void guardar(Usuario usuario) {
        int insertadas = tabla.insertarSiElCorreoEstaLibre(
                usuario.id(),
                usuario.correoOriginal(),
                usuario.correo().valor(),
                usuario.nombre(),
                usuario.hashContrasena(),
                usuario.rol().name(),
                usuario.estado().name());

        if (insertadas == 0) {
            throw new CorreoYaRegistrado();
        }
    }
}
