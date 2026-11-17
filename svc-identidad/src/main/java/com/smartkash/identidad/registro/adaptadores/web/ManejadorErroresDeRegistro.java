package com.smartkash.identidad.registro.adaptadores.web;

import com.smartkash.identidad.registro.dominio.ErrorDeRegistro;
import com.smartkash.nucleo.web.errores.ErrorCampo;
import com.smartkash.nucleo.web.errores.Problemas;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Convierte los errores de las reglas del registro en respuestas de error estándar.
 *
 * <p>Qué hace: cuando el caso de uso rechaza un dato (contraseña débil, política sin
 * aceptar), arma una respuesta 400 que señala el campo y explica la regla.</p>
 *
 * <p>De dónde vienen los datos: de {@link ErrorDeRegistro}, que trae el campo, el mensaje y
 * el motivo. A dónde van: al cuerpo de la respuesta. El motivo viaja en {@code codigo} para
 * que la pantalla pueda reaccionar sin leer el texto; por ejemplo, mostrar la política nueva
 * cuando la versión aceptada ya no es la vigente.</p>
 *
 * <p>Reglas: el mensaje del error nunca contiene el valor rechazado, así que es seguro
 * devolverlo. Tiene prioridad ({@code @Order}) sobre el manejador común, que de otro modo
 * trataría este error como un fallo inesperado.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ManejadorErroresDeRegistro {

    /**
     * Responde a un dato del registro que incumple una regla de negocio.
     *
     * @param error la regla incumplida
     * @return el error de datos de entrada, con un único elemento en la lista de errores
     */
    @ExceptionHandler(ErrorDeRegistro.class)
    ProblemDetail datoRechazado(ErrorDeRegistro error) {
        ProblemDetail problema = Problemas.validacion("Revisa los campos indicados.",
                List.of(new ErrorCampo(error.campo(), error.getMessage())));
        problema.setProperty(Problemas.CODIGO, error.motivo().name());
        return problema;
    }
}
