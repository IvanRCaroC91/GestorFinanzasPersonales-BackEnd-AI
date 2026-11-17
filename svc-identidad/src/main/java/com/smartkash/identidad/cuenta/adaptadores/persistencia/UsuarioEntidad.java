package com.smartkash.identidad.cuenta.adaptadores.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Fila de la tabla de usuarios vista desde el código.
 *
 * <p>Qué es: una entidad, es decir, una clase que JPA sabe relacionar con una tabla. Cada
 * objeto representa una fila y cada campo una columna. No es la clase de negocio
 * {@code Usuario}: aquella contiene las reglas y no sabe nada de la base de datos; esta solo
 * describe cómo está guardada.</p>
 *
 * <p>Qué columnas declara: las que hacen falta para reconstruir una cuenta al leerla
 * (identificador, correo, nombre, hash, rol y estado). Las fechas de verificación y de
 * eliminación programada se añadirán cuando alguna funcionalidad tenga que leerlas. Las
 * cuentas no se insertan a través de esta clase, sino con la consulta de
 * {@link UsuariosJpa#insertarSiElCorreoEstaLibre}.</p>
 *
 * <p>Solo lectura: la clase no tiene métodos para cambiar valores, así que una fila leída
 * no puede modificarse por accidente. El rol y el estado se guardan como texto, tal como
 * están en la tabla; quien los convierte a los tipos del negocio es
 * {@link RepositorioUsuariosPostgres}.</p>
 *
 * <p>Regla: al arrancar, se comprueba que estas columnas existen en la tabla que crearon las
 * migraciones; si no, el servicio no arranca.</p>
 */
@Entity
@Table(name = "usuarios")
class UsuarioEntidad {

    @Id
    private UUID id;

    @Column(name = "email")
    private String email;

    @Column(name = "email_normalizado")
    private String emailNormalizado;

    @Column(name = "nombre")
    private String nombre;

    @Column(name = "hash_contrasena")
    private String hashContrasena;

    @Column(name = "rol")
    private String rol;

    @Column(name = "estado")
    private String estado;

    /** JPA exige un constructor sin parámetros para poder crear el objeto al leer una fila. */
    protected UsuarioEntidad() {
    }

    /** @return identificador de la cuenta */
    UUID id() {
        return id;
    }

    /** @return correo como lo escribió la persona */
    String email() {
        return email;
    }

    /** @return el mismo correo en minúsculas y sin espacios */
    String emailNormalizado() {
        return emailNormalizado;
    }

    /** @return nombre para mostrar */
    String nombre() {
        return nombre;
    }

    /** @return hash de la contraseña, nunca la contraseña */
    String hashContrasena() {
        return hashContrasena;
    }

    /** @return rol de la cuenta, con el texto exacto que hay en la tabla */
    String rol() {
        return rol;
    }

    /** @return estado de la cuenta, con el texto exacto que hay en la tabla */
    String estado() {
        return estado;
    }
}