package com.smartkash.identidad.registro.aplicacion;

/**
 * Datos que una persona envía para crear su cuenta, tal como llegan.
 *
 * <p>Qué hace: agrupa en un solo objeto lo que el caso de uso del registro necesita, para
 * no pasarle cinco parámetros sueltos. No valida nada: las reglas las aplica el caso de
 * uso con ayuda de las clases del dominio.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo de la solicitud de registro; la capa web los
 * copia aquí. A dónde van: al caso de uso {@link RegistrarUsuario}.</p>
 *
 * <p>Reglas: solo contiene los cinco datos que la persona puede decidir. No tiene rol,
 * estado ni identificador, porque esos los pone el servidor.</p>
 *
 * @param nombre          nombre para mostrar
 * @param correo          correo como lo escribió la persona
 * @param contrasena      contraseña en claro, sin recortar; solo vive mientras dura la solicitud
 * @param aceptaPolitica  si la persona marcó que acepta la política de tratamiento de datos
 * @param versionPolitica versión de la política que vio la persona
 */
public record DatosDeRegistro(
        String nombre,
        String correo,
        String contrasena,
        boolean aceptaPolitica,
        String versionPolitica) {

    /**
     * Devuelve un texto corto para depuración que no revela nada sensible.
     *
     * <p>Un record normalmente muestra todos sus campos; aquí se omiten la contraseña y
     * los datos personales para que no terminen escritos en un log por accidente.</p>
     *
     * @return texto con lo único que es seguro mostrar: la política aceptada y su versión
     */
    @Override
    public String toString() {
        return "DatosDeRegistro[aceptaPolitica=" + aceptaPolitica
                + ", versionPolitica=" + versionPolitica + "]";
    }
}
