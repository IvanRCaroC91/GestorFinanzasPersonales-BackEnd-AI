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
 * <p>Qué columnas declara: por ahora solo las que el registro necesita consultar (el
 * identificador y el correo normalizado). Las demás se añadirán cuando alguna funcionalidad
 * tenga que leerlas, por ejemplo el inicio de sesión. Las cuentas no se insertan a través de
 * esta clase, sino con la consulta de {@link UsuariosJpa#insertarSiElCorreoEstaLibre}.</p>
 *
 * <p>Regla: al arrancar, se comprueba que estas columnas existen en la tabla que crearon las
 * migraciones; si no, el servicio no arranca.</p>
 */
@Entity
@Table(name = "usuarios")
class UsuarioEntidad {

    @Id
    private UUID id;

    @Column(name = "email_normalizado")
    private String emailNormalizado;

    /** JPA exige un constructor sin parámetros para poder crear el objeto al leer una fila. */
    protected UsuarioEntidad() {
    }
}