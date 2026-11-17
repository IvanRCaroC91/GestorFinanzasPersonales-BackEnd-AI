package com.smartkash.identidad.verificacion.adaptadores.web;

import com.smartkash.identidad.verificacion.aplicacion.VerificarCorreo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puerta de entrada web de la verificación del correo.
 *
 * <p>De dónde vienen los datos: del cuerpo JSON de {@code POST /api/v1/auth/verificacion-correo},
 * una ruta pública: quien verifica puede no tener la sesión iniciada en ese navegador. A dónde
 * van: al caso de uso {@link VerificarCorreo}. Si el enlace no sirve, el error lo convierte
 * {@link ManejadorErroresDeVerificacion}.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
class VerificacionCorreoController {

    private final VerificarCorreo verificarCorreo;

    /**
     * @param verificarCorreo el caso de uso; lo entrega Spring
     */
    VerificacionCorreoController(VerificarCorreo verificarCorreo) {
        this.verificarCorreo = verificarCorreo;
    }

    /**
     * Verifica el correo con el token del enlace.
     *
     * <p>Responde 204 («sin contenido»): la operación se hizo y no hay nada que devolver.</p>
     *
     * @param solicitud el token, ya validado en su forma
     */
    @PostMapping("/verificacion-correo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verificar(@Valid @RequestBody VerificacionSolicitud solicitud) {
        verificarCorreo.ejecutar(solicitud.token());
    }
}