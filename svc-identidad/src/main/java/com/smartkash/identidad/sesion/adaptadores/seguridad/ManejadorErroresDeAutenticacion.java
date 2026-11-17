package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.nucleo.web.errores.Problemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte cualquier fallo de autenticación en la respuesta {@code 401} estándar del
 * servicio.
 *
 * <p>Cómo llega aquí: los filtros de seguridad actúan antes que los controladores, así que sus
 * errores no pasarían por ningún manejador de errores. El punto de entrada definido en
 * {@link ConfiguracionDeSeguridad} se los reenvía a Spring MVC, que los trae a esta clase. Así
 * el {@code 401} usa el mismo formato RFC 9457 y el mismo conversor JSON que el resto de
 * errores.</p>
 *
 * <p>Regla: la respuesta es idéntica sea cual sea el motivo (token ausente, caducado, mal
 * firmado, de otro emisor o destinatario). La cabecera {@code WWW-Authenticate} solo dice
 * {@code Bearer}, que es lo que pide el estándar público RFC 6750 (cómo se envían tokens en
 * HTTP): la que Spring pone por defecto añade el motivo del rechazo. El motivo se escribe solo
 * en el log, a nivel de depuración y sin el token.</p>
 *
 * <p>{@code @Order}: va por delante del manejador común, que atiende cualquier excepción y la
 * convertiría en un {@code 500}.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresDeAutenticacion {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresDeAutenticacion.class);

    /** Explicación única para todos los rechazos de token. */
    static final String DETALLE = "Se requiere un token de acceso válido.";

    /**
     * Responde {@code 401} con el error estándar.
     *
     * @param error el motivo del rechazo; solo se usa para el log
     * @return la respuesta, igual para cualquier motivo
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> noAutenticado(AuthenticationException error) {
        LOG.debug("Petición rechazada por falta de autenticación válida: {}",
                error.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.noAutenticado(DETALLE));
    }
}