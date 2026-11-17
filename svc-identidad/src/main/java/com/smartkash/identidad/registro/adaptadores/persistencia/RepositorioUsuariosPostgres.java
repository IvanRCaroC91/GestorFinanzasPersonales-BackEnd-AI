package com.smartkash.identidad.registro.adaptadores.persistencia;

import com.smartkash.identidad.registro.dominio.CorreoNormalizado;
import com.smartkash.identidad.registro.dominio.CorreoYaRegistrado;
import com.smartkash.identidad.registro.dominio.Usuario;
import com.smartkash.identidad.registro.puertos.RepositorioUsuarios;
import org.springframework.stereotype.Repository;

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
 * <p>De dónde vienen los datos: del caso de uso del registro. A dónde van: a la tabla de
 * usuarios.</p>
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
