package com.smartkash.identidad.verificacion.adaptadores.web;

import com.smartkash.identidad.verificacion.aplicacion.ReenviarVerificacion;
import com.smartkash.identidad.verificacion.aplicacion.VerificarCorreo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Puerta de entrada web de la verificación del correo: verificar y pedir otro enlace.
 *
 * <p>Las dos rutas:</p>
 * <ul>
 *   <li>{@code POST /api/v1/auth/verificacion-correo}: pública, porque quien verifica puede no
 *       tener la sesión iniciada en ese navegador. El token del enlace llega en el cuerpo.</li>
 *   <li>{@code POST /api/v1/auth/verificacion-correo/reenvio}: exige token de acceso. La cuenta
 *       sale de ese token; la petición no lleva cuerpo, y si lo lleva se ignora.</li>
 * </ul>
 *
 * <p>Los errores los convierte {@link ManejadorErroresDeVerificacion}.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/verificacion-correo")
class VerificacionCorreoController {

    /** Mensaje único del reenvío, igual se haya enviado el correo o no. */
    static final String MENSAJE_REENVIO =
            "Si tu correo está pendiente de verificar, te hemos enviado un enlace nuevo.";

    private final VerificarCorreo verificarCorreo;
    private final ReenviarVerificacion reenviarVerificacion;

    /**
     * @param verificarCorreo      el caso de uso de verificación; lo entrega Spring
     * @param reenviarVerificacion el caso de uso de reenvío; lo entrega Spring
     */
    VerificacionCorreoController(VerificarCorreo verificarCorreo, ReenviarVerificacion reenviarVerificacion) {
        this.verificarCorreo = verificarCorreo;
        this.reenviarVerificacion = reenviarVerificacion;
    }

    /**
     * Verifica el correo con el token del enlace.
     *
     * <p>Responde 204 («sin contenido»): la operación se hizo y no hay nada que devolver.</p>
     *
     * @param solicitud el token, ya validado en su forma
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verificar(@Valid @RequestBody VerificacionSolicitud solicitud) {
        verificarCorreo.ejecutar(solicitud.token());
    }

    /**
     * Pide otro enlace de verificación para la cuenta del token de acceso.
     *
     * <p>Qué es {@code @AuthenticationPrincipal}: le pide a Spring Security el token que ya
     * validó el filtro de seguridad. De él sale la cuenta ({@code sub}), y de ningún otro
     * sitio.</p>
     *
     * <p>Responde 202 («aceptada») con el mismo mensaje esté la cuenta pendiente o ya
     * verificada.</p>
     *
     * @param token el token de acceso ya validado
     * @return el mensaje fijo
     */
    @PostMapping("/reenvio")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MensajeRespuesta reenviar(@AuthenticationPrincipal Jwt token) {
        reenviarVerificacion.ejecutar(UUID.fromString(token.getSubject()));
        return new MensajeRespuesta(MENSAJE_REENVIO);
    }

    /**
     * Cuerpo de una respuesta que solo lleva un mensaje.
     *
     * @param mensaje el texto para la persona
     */
    record MensajeRespuesta(String mensaje) {
    }
}