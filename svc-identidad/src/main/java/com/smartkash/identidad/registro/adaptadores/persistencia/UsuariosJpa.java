package com.smartkash.identidad.registro.adaptadores.persistencia;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consultas sobre la tabla de usuarios.
 *
 * <p>Qué es: una interfaz de Spring Data. No tiene clase que la implemente: Spring crea el
 * código al arrancar, a partir del nombre de cada método o de la consulta escrita en
 * {@code @Query}. Solo la usa {@link RepositorioUsuariosPostgres}.</p>
 */
interface UsuariosJpa extends Repository<UsuarioEntidad, UUID> {

    /**
     * Indica si hay una cuenta con ese correo. Spring deduce la consulta del nombre del
     * método: "existe por correo normalizado".
     *
     * @param emailNormalizado el correo en minúsculas y sin espacios
     * @return {@code true} si existe una fila con ese correo
     */
    boolean existsByEmailNormalizado(String emailNormalizado);

    /**
     * Inserta una cuenta solo si su correo no está ya registrado.
     *
     * <p>Por qué con SQL propio: {@code ON CONFLICT DO NOTHING} le pide a PostgreSQL que,
     * si el índice único del correo detecta un duplicado, simplemente no inserte, en lugar
     * de lanzar un error. Un error dejaría inservible la transacción en curso, y el registro
     * tiene que poder terminar con normalidad cuando el correo ya existe. Quien impide el
     * duplicado sigue siendo el índice único, así que también funciona si dos solicitudes
     * iguales llegan a la vez.</p>
     *
     * <p>Las fechas de creación y modificación y el contador de versión no se envían: la
     * tabla les pone su valor inicial.</p>
     *
     * <p>{@code @Transactional}: una transacción es un grupo de cambios que se guardan
     * todos o ninguno. Aquí el método se une a la que ya esté abierta o abre una propia.</p>
     *
     * @param id               identificador de la cuenta
     * @param email            correo como lo escribió la persona
     * @param emailNormalizado el mismo correo en su forma única
     * @param nombre           nombre para mostrar
     * @param hashContrasena   hash de la contraseña, nunca la contraseña
     * @param rol              rol decidido por el servidor
     * @param estado           estado decidido por el servidor
     * @return 1 si se insertó la cuenta; 0 si ya había una con ese correo
     */
    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO usuarios (id, email, email_normalizado, nombre, hash_contrasena, rol, estado)
            VALUES (:id, :email, :emailNormalizado, :nombre, :hashContrasena, :rol, :estado)
            ON CONFLICT (email_normalizado) DO NOTHING
            """)
    int insertarSiElCorreoEstaLibre(
            @Param("id") UUID id,
            @Param("email") String email,
            @Param("emailNormalizado") String emailNormalizado,
            @Param("nombre") String nombre,
            @Param("hashContrasena") String hashContrasena,
            @Param("rol") String rol,
            @Param("estado") String estado);
}