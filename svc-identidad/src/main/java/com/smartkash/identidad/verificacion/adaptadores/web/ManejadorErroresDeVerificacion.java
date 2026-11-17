package com.smartkash.identidad.verificacion.adaptadores.web;

import com.smartkash.identidad.verificacion.dominio.DemasiadosEnvios;
import com.smartkash.identidad.verificacion.dominio.EnlaceNoValido;
import com.smartkash.nucleo.web.errores.Problemas;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;

/**
 * Convierte los errores de la verificación del correo en respuestas de error estándar.
 *
 * <p>Qué hace:</p>
 * <ul>
 *   <li>Un enlace que no sirve pasa a {@code 400} de regla de negocio con el código
 *       {@value EnlaceNoValido#CODIGO}. La respuesta es la misma para un enlace inexistente,
 *       caducado o usado, porque el error y su mensaje son siempre los mismos.</li>
 *   <li>Un reenvío por encima del tope pasa a {@code 429} con la cabecera {@code Retry-After}.</li>
 * </ul>
 *
 * <p>Tiene prioridad ({@code @Order}) sobre el manejador común, que de otro modo los trataría
 * como fallos inesperados.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresDeVerificacion {

    /**
     * Responde a un enlace que no sirve.
     *
     * @param error el error genérico; no lleva datos
     * @return el error de regla de negocio
     */
    @ExceptionHandler(EnlaceNoValido.class)
    ProblemDetail enlaceNoValido(EnlaceNoValido error) {
        return Problemas.reglaNegocio(EnlaceNoValido.MENSAJE, EnlaceNoValido.CODIGO);
    }

    /**
     * Responde {@code 429} cuando ya se pidieron demasiados enlaces en la última hora.
     *
     * @param error el rechazo, con el tiempo que falta
     * @return la respuesta con la espera en {@code Retry-After}
     */
    @ExceptionHandler(DemasiadosEnvios.class)
    ResponseEntity<ProblemDetail> demasiadosEnvios(DemasiadosEnvios error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosDeEspera(error.espera())))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.demasiadasSolicitudes(error.getMessage()));
    }

    /*
     * Segundos enteros para Retry-After, redondeados hacia arriba y nunca menos de 1. Es la
     * misma regla que en el inicio de sesión; se repite aquí porque la librería común todavía no
     * tiene pruebas propias donde comprobarla.
     */
    static long segundosDeEspera(Duration espera) {
        long segundos = espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}
