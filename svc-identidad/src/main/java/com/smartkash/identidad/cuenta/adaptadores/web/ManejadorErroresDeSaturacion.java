package com.smartkash.identidad.cuenta.adaptadores.web;

import com.smartkash.identidad.cuenta.puertos.HasheadorSaturado;
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

/**
 * Convierte la saturación del cálculo de contraseñas en una respuesta {@code 429} con
 * {@code Retry-After}.
 *
 * <p>Dónde aplica: en cualquier ruta que calcule un hash de contraseña. Hoy son el registro y
 * el inicio de sesión. Por eso vive en el paquete compartido de cuentas y no en cada
 * funcionalidad.</p>
 *
 * <p>Por qué {@code 429} y no {@code 503}: el catálogo de errores del contrato no tiene un tipo
 * para «servicio ocupado». El {@code 503} que contempla es para cuando falla otro servicio del
 * que se depende, que no es el caso. El {@code 429} de demasiadas solicitudes, con
 * {@code Retry-After}, le dice al cliente exactamente lo que tiene que hacer: esperar un momento
 * y reintentar. Es el mismo tipo que ya usa el bloqueo por intentos fallidos, pero con otra
 * explicación, para que la persona no crea que su cuenta está bloqueada.</p>
 *
 * <p>{@code @Order}: va por delante del manejador común, que convertiría el error en un
 * {@code 500}.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErroresDeSaturacion {

    /** Explicación que recibe la persona. */
    public static final String DETALLE =
            "El servicio está atendiendo muchas solicitudes. Inténtalo de nuevo en unos segundos.";

    /**
     * Segundos que se piden al cliente antes de reintentar. Un turno suele liberarse en
     * milisegundos, así que basta con el mínimo que admite la cabecera.
     */
    static final int SEGUNDOS_ANTES_DE_REINTENTAR = 1;

    /**
     * Constructor vacío. Spring lo usa al registrar el manejador.
     */
    public ManejadorErroresDeSaturacion() {
        // Sin trabajo propio: el comportamiento lo da el método de la clase.
    }

    /**
     * Responde {@code 429} con el error estándar de demasiadas solicitudes.
     *
     * @param error la saturación; no lleva datos
     * @return la respuesta con {@code Retry-After}
     */
    @ExceptionHandler(HasheadorSaturado.class)
    ResponseEntity<ProblemDetail> saturado(HasheadorSaturado error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(SEGUNDOS_ANTES_DE_REINTENTAR))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Problemas.demasiadasSolicitudes(DETALLE));
    }
}
