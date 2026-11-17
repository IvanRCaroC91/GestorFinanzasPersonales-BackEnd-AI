package com.smartkash.identidad.sesion.adaptadores.web;

import com.smartkash.identidad.sesion.dominio.CredencialesIncorrectas;
import com.smartkash.identidad.sesion.dominio.DemasiadosIntentos;
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
 * Convierte los errores del inicio de sesión en respuestas de error estándar.
 *
 * <p>Qué hace:</p>
 * <ul>
 *   <li>{@link CredencialesIncorrectas} pasa a {@code 401} con el mensaje genérico. Como el
 *       caso de uso lanza el mismo error para cualquier motivo, la respuesta es idéntica para
 *       una contraseña errónea, un correo sin cuenta o una cuenta que no puede entrar.</li>
 *   <li>{@link DemasiadosIntentos} pasa a {@code 429} con la cabecera {@code Retry-After}: los
 *       segundos que hay que esperar.</li>
 * </ul>
 *
 * <p>Tiene prioridad ({@code @Order}) sobre el manejador común, que de otro modo trataría estos
 * errores como fallos inesperados.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresDeInicioSesion {

    /**
     * Responde {@code 401} a unas credenciales que no sirven.
     *
     * <p>Lleva {@code WWW-Authenticate: Bearer}, igual que el {@code 401} de las rutas
     * protegidas, porque el estándar HTTP pide esa cabecera en todo {@code 401}.</p>
     *
     * @param error el error genérico; no lleva datos
     * @return la respuesta, idéntica para cualquier motivo
     */
    @ExceptionHandler(CredencialesIncorrectas.class)
    ResponseEntity<ProblemDetail> credencialesIncorrectas(CredencialesIncorrectas error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.noAutenticado(CredencialesIncorrectas.MENSAJE));
    }

    /**
     * Responde {@code 429} cuando hay un bloqueo en curso.
     *
     * @param error el bloqueo, con el tiempo que falta
     * @return la respuesta con la espera en {@code Retry-After}
     */
    @ExceptionHandler(DemasiadosIntentos.class)
    ResponseEntity<ProblemDetail> demasiadosIntentos(DemasiadosIntentos error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosDeEspera(error.espera())))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.demasiadasSolicitudes(
                        "Demasiados intentos fallidos. Espera antes de volver a intentarlo."));
    }

    /**
     * Convierte la espera en segundos enteros para {@code Retry-After}.
     *
     * <p>Redondea siempre hacia arriba y nunca devuelve menos de 1: con un redondeo hacia abajo,
     * quien obedece la cabecera volvería justo antes de que termine el bloqueo.</p>
     *
     * @param espera el tiempo que falta
     * @return los segundos, al menos 1
     */
    static long segundosDeEspera(Duration espera) {
        long segundos = espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}