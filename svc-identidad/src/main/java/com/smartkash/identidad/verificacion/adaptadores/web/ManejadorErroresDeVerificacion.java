package com.smartkash.identidad.verificacion.adaptadores.web;

import com.smartkash.identidad.verificacion.dominio.EnlaceNoValido;
import com.smartkash.nucleo.web.errores.Problemas;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte los errores de la verificación del correo en respuestas de error estándar.
 *
 * <p>Qué hace: un enlace que no sirve pasa a {@code 400} de regla de negocio con el código
 * {@value EnlaceNoValido#CODIGO}. La respuesta es la misma para un enlace inexistente,
 * caducado o usado, porque el error y su mensaje son siempre los mismos.</p>
 *
 * <p>Tiene prioridad ({@code @Order}) sobre el manejador común, que de otro modo lo trataría
 * como un fallo inesperado.</p>
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
}
