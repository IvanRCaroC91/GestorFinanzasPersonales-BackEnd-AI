package com.smartkash.nucleo.web.errores;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.util.Comparator;
import java.util.List;

/**
 * Convierte en respuestas de error estándar los fallos que son iguales en todos los servicios.
 *
 * <p>Qué es: un manejador de errores. {@code @RestControllerAdvice} le dice a Spring que,
 * cuando un controlador lance una excepción, busque aquí un método que sepa convertirla en
 * una respuesta. Así hay un solo lugar que decide cómo se ve un error, en vez de repetirlo
 * en cada controlador.</p>
 *
 * <p>Qué cubre: los datos que no cumplen las reglas de forma declaradas en la solicitud, los
 * cuerpos que no se pueden leer (JSON mal escrito o con campos no permitidos) y cualquier
 * fallo inesperado. Los demás errores habituales de la web (ruta inexistente, método no
 * permitido) los resuelve la clase de Spring de la que hereda. Los errores propios de cada
 * funcionalidad los maneja cada servicio.</p>
 *
 * <p>Reglas: la respuesta nunca incluye detalles internos ni los valores enviados; esos
 * detalles van al log. Va en último lugar ({@code @Order}) para que los manejadores de cada
 * servicio tengan prioridad sobre este.</p>
 *
 * <p>Cómo se usa: cada servicio lo activa importándolo en su clase de arranque.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ManejadorErroresComun extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresComun.class);

    /** Largo máximo del nombre de un campo desconocido al escribirlo en el log. */
    private static final int LARGO_MAXIMO_EN_LOG = 100;

    /**
     * Datos que no cumplen las reglas de forma de la solicitud (campo obligatorio ausente,
     * formato incorrecto). Responde 400 con un elemento por cada problema.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest solicitud) {

        // Se ordenan para que la misma solicitud reciba siempre la misma respuesta.
        List<ErrorCampo> errores = error.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorCampo(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorCampo::campo).thenComparing(ErrorCampo::mensaje))
                .toList();

        ProblemDetail problema = Problemas.validacion("Revisa los campos indicados.", errores);
        return handleExceptionInternal(error, problema, cabeceras, estado, solicitud);
    }

    /**
     * Cuerpo que no se puede leer: JSON mal escrito, de un tipo equivocado o con un campo que
     * el servicio no admite. Responde 400 sin repetir nada de lo que se envió.
     *
     * <p>Un campo no admitido puede ser un intento de fijar datos que solo decide el
     * servidor (como el rol), así que se deja un aviso en el log con el nombre del campo,
     * nunca con su valor.</p>
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException error, HttpHeaders cabeceras,
            HttpStatusCode estado, WebRequest solicitud) {

        String detalle = "El cuerpo de la solicitud no se puede leer. Revisa su formato.";
        if (error.getMostSpecificCause() instanceof UnrecognizedPropertyException desconocido) {
            LOG.warn("Solicitud rechazada por traer un campo no admitido: {}",
                    seguroParaLog(desconocido.getPropertyName()));
            detalle = "La solicitud contiene un campo que no está permitido.";
        }

        ProblemDetail problema = Problemas.validacion(detalle, List.of());
        return handleExceptionInternal(error, problema, cabeceras, estado, solicitud);
    }

    /**
     * Cualquier fallo que nadie más supo manejar. Responde 500 con un mensaje genérico.
     *
     * @param error el fallo inesperado; se escribe completo en el log para poder investigarlo
     * @return el error interno, sin ningún detalle del fallo
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception error) {
        LOG.error("Error no controlado", error);
        return Problemas.interno();
    }

    /*
     * El nombre del campo lo escribe quien envía la solicitud. Se recorta y se le quitan los
     * saltos de línea para que no pueda llenar el log ni falsificar líneas en él.
     */
    private static String seguroParaLog(String texto) {
        if (texto == null) {
            return "(sin nombre)";
        }
        String limpio = texto.replaceAll("[\\r\\n\\t]", "_");
        return limpio.length() <= LARGO_MAXIMO_EN_LOG
                ? limpio
                : limpio.substring(0, LARGO_MAXIMO_EN_LOG) + "...";
    }
}
