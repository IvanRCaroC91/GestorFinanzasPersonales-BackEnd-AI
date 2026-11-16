package com.smartkash.identidad.registro.dominio;

import com.smartkash.identidad.registro.dominio.ErrorDeRegistro.Motivo;

import java.util.Objects;
import java.util.UUID;

/**
 * Cuenta de una persona en el sistema.
 *
 * <p>Qué hace: reúne los datos de la cuenta y garantiza que siempre sean coherentes: el
 * nombre tiene un largo válido, el correo original y el normalizado son la misma
 * dirección, y nunca falta el hash de la contraseña.</p>
 *
 * <p>De dónde vienen los datos: en el registro, el nombre y el correo vienen del cuerpo
 * de la solicitud y el hash lo calcula el servidor. El identificador, el rol y el estado
 * los decide el servidor (ver {@link #nuevo}). A dónde van: a la tabla de usuarios, a
 * través del repositorio de usuarios.</p>
 *
 * <p>Qué no guarda: la contraseña en claro (solo su hash), ni las fechas de creación y
 * modificación, que las pone la capa de persistencia.</p>
 *
 * <p>Reglas y errores: lanza {@link ErrorDeRegistro} si el nombre falta o es demasiado
 * largo, porque ese es un dato que escribe la persona. Lanza
 * {@link NullPointerException} o {@link IllegalArgumentException} si falta cualquier
 * otro dato o los correos no corresponden, porque eso sería un fallo del programa.</p>
 *
 * @param id             identificador único de la cuenta
 * @param nombre         nombre para mostrar, sin espacios en los extremos
 * @param correoOriginal correo como lo escribió la persona (con sus mayúsculas), sin
 *                       espacios en los extremos
 * @param correo         el mismo correo en su forma única, usado para buscar y evitar
 *                       duplicados
 * @param hashContrasena resultado de aplicar el algoritmo de protección a la contraseña;
 *                       nunca la contraseña en claro
 * @param rol            qué puede hacer la cuenta
 * @param estado         en qué punto de su vida está la cuenta
 */
public record Usuario(
        UUID id,
        String nombre,
        String correoOriginal,
        CorreoNormalizado correo,
        String hashContrasena,
        Rol rol,
        EstadoCuenta estado) {

    /** Cantidad máxima de caracteres del nombre. */
    public static final int LARGO_MAXIMO_NOMBRE = 100;

    /** Nombre del campo en el cuerpo de la solicitud; viaja en el error. */
    private static final String CAMPO_NOMBRE = "nombre";

    /**
     * Valida los datos y los deja en su forma definitiva al crear el objeto.
     *
     * @throws ErrorDeRegistro          si el nombre es nulo, queda vacío o es demasiado largo
     * @throws NullPointerException     si falta cualquier otro dato
     * @throws IllegalArgumentException si el hash está vacío o el correo original no
     *                                  corresponde al correo normalizado
     */
    public Usuario {
        Objects.requireNonNull(id, "El identificador es obligatorio.");
        Objects.requireNonNull(correoOriginal, "El correo original es obligatorio.");
        Objects.requireNonNull(correo, "El correo normalizado es obligatorio.");
        Objects.requireNonNull(hashContrasena, "El hash de la contraseña es obligatorio.");
        Objects.requireNonNull(rol, "El rol es obligatorio.");
        Objects.requireNonNull(estado, "El estado es obligatorio.");

        if (nombre == null || nombre.isBlank()) {
            throw new ErrorDeRegistro(
                    Motivo.NOMBRE_OBLIGATORIO, CAMPO_NOMBRE, "El nombre es obligatorio.");
        }
        nombre = nombre.strip();
        // Se cuentan caracteres como los ve la persona: un emoji vale uno, no dos.
        if (nombre.codePointCount(0, nombre.length()) > LARGO_MAXIMO_NOMBRE) {
            throw new ErrorDeRegistro(Motivo.NOMBRE_MUY_LARGO, CAMPO_NOMBRE,
                    "El nombre no puede tener más de " + LARGO_MAXIMO_NOMBRE + " caracteres.");
        }

        // Se conservan las mayúsculas que escribió la persona, pero no los espacios de
        // los extremos: no forman parte de la dirección y podrían hacer que el valor no
        // quepa en la columna donde se guarda.
        correoOriginal = correoOriginal.strip();

        // Los dos correos deben ser la misma dirección; si no, la cuenta quedaría con un
        // correo visible distinto del que se usa para buscarla. El mensaje no los incluye.
        if (!new CorreoNormalizado(correoOriginal).equals(correo)) {
            throw new IllegalArgumentException(
                    "El correo original no corresponde al correo normalizado.");
        }

        if (hashContrasena.isBlank()) {
            throw new IllegalArgumentException("El hash de la contraseña es obligatorio.");
        }
    }

    /**
     * Crea la cuenta de una persona que se acaba de registrar.
     *
     * <p>Quien llama solo entrega lo que viene de la persona y el hash ya calculado. El
     * identificador se genera aquí al azar, el rol es siempre {@link Rol#USUARIO} y el
     * estado siempre {@link EstadoCuenta#PENDIENTE_VERIFICACION}. Como este método no
     * recibe rol ni estado, una solicitud de registro no puede elegirlos.</p>
     *
     * @param nombre         nombre que escribió la persona
     * @param correoOriginal correo como lo escribió la persona
     * @param correo         el mismo correo ya normalizado
     * @param hashContrasena hash de la contraseña ya calculado
     * @return la cuenta nueva, lista para guardarse
     * @throws ErrorDeRegistro          si el nombre no es válido
     * @throws NullPointerException     si falta el correo o el hash
     * @throws IllegalArgumentException si el hash está vacío o los correos no corresponden
     */
    public static Usuario nuevo(
            String nombre, String correoOriginal, CorreoNormalizado correo, String hashContrasena) {
        return new Usuario(UUID.randomUUID(), nombre, correoOriginal, correo, hashContrasena,
                Rol.USUARIO, EstadoCuenta.PENDIENTE_VERIFICACION);
    }

    /**
     * Devuelve un texto corto para depuración.
     *
     * <p>Un record normalmente muestra todos sus campos. Aquí se muestran solo el
     * identificador, el rol y el estado, para que ni el hash ni los datos personales
     * (nombre, correo) terminen escritos en un log por accidente.</p>
     *
     * @return texto con el identificador, el rol y el estado
     */
    @Override
    public String toString() {
        return "Usuario[id=" + id + ", rol=" + rol + ", estado=" + estado + "]";
    }
}
