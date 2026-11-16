package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.registro.aplicacion.DatosDeRegistro;

/**
 * Cuerpo de la solicitud de registro, tal como llega en JSON.
 *
 * <p>Qué es: un record, es decir, una clase que solo agrupa datos y no cambia después de
 * crearse. Spring lo llena con el JSON de la petición: cada campo del JSON va al componente
 * del mismo nombre.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo de la petición que envía el frontend. A dónde
 * van: se copian a {@link DatosDeRegistro} para el caso de uso, con {@link #aDatos()}.</p>
 *
 * <p>Reglas: declara solo los cinco campos que la persona puede decidir. No hay rol, estado
 * ni identificador, así que una petición no puede elegirlos; además, el servicio rechaza
 * cualquier petición que traiga un campo no declarado aquí. Esta clase no comprueba las
 * reglas de negocio (largo de la contraseña, política aceptada): eso lo hace el caso de
 * uso.</p>
 *
 * @param nombre          nombre para mostrar
 * @param correo          correo como lo escribió la persona
 * @param contrasena      contraseña en claro; solo se recibe, nunca se devuelve
 * @param aceptaPolitica  si la persona aceptó la política de tratamiento de datos; puede
 *                        faltar en el JSON, y entonces vale {@code null}
 * @param versionPolitica versión de la política que vio la persona
 */
record RegistroSolicitud(
        String nombre,
        String correo,
        String contrasena,
        Boolean aceptaPolitica,
        String versionPolitica) {

    /**
     * Copia los datos al objeto que entiende el caso de uso.
     *
     * <p>Regla: si la petición no dijo si acepta la política, se toma como no aceptada.</p>
     *
     * @return los datos de registro, sin transformar la contraseña ni el correo
     */
    DatosDeRegistro aDatos() {
        return new DatosDeRegistro(
                nombre, correo, contrasena, Boolean.TRUE.equals(aceptaPolitica), versionPolitica);
    }

    /**
     * Devuelve un texto para depuración que no revela nada sensible.
     *
     * <p>Un record normalmente muestra todos sus campos; aquí se omiten la contraseña y los
     * datos personales para que no terminen escritos en un log por accidente.</p>
     *
     * @return texto con lo único que es seguro mostrar
     */
    @Override
    public String toString() {
        return "RegistroSolicitud[aceptaPolitica=" + aceptaPolitica
                + ", versionPolitica=" + versionPolitica + "]";
    }
}
