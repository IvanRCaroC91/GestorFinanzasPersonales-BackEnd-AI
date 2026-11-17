package com.smartkash.identidad.registro.dominio;

/**
 * Aviso de que no se pudo guardar una cuenta porque ya existe otra con el mismo correo.
 *
 * <p>Qué hace: permite que el repositorio de usuarios informe del duplicado con palabras
 * del negocio, sin que el resto del programa tenga que conocer los errores propios de la
 * base de datos.</p>
 *
 * <p>De dónde viene: lo lanza el repositorio de usuarios cuando el índice único de la
 * base de datos rechaza el correo. Ocurre, por ejemplo, si dos solicitudes con el mismo
 * correo llegan casi a la vez y ambas creen que el correo está libre. A dónde va: lo
 * captura el caso de uso del registro, que responde igual que si la cuenta se hubiera
 * creado, para no revelar que el correo ya existía.</p>
 *
 * <p>Reglas: el mensaje no incluye el correo, para que no termine escrito en los logs.</p>
 */
public class CorreoYaRegistrado extends RuntimeException {

    private static final String MENSAJE = "Ya existe una cuenta con ese correo.";

    /** Crea el aviso sin más detalle. */
    public CorreoYaRegistrado() {
        super(MENSAJE);
    }

    /**
     * Crea el aviso conservando el error original que lo provocó, útil para diagnosticar.
     *
     * @param causa el error de la capa de persistencia que detectó el duplicado
     */
    public CorreoYaRegistrado(Throwable causa) {
        super(MENSAJE, causa);
    }
}
