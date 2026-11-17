package com.smartkash.nucleo.web.errores;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.List;

/**
 * Fábrica de respuestas de error con los tipos acordados para toda la plataforma.
 *
 * <p>Qué hace: crea objetos {@link ProblemDetail}, la clase de Spring que representa un error
 * en el formato RFC 9457 (un estándar público para describir errores de una API web). Cada
 * error lleva un tipo, un título, el código HTTP y una explicación. Tener los tipos y los
 * títulos en un solo sitio evita que cada servicio los escriba a su manera.</p>
 *
 * <p>De dónde vienen los datos: de los manejadores de errores de cada servicio. A dónde van:
 * Spring convierte el objeto en el cuerpo JSON de la respuesta.</p>
 *
 * <p>Regla: la explicación está pensada para la persona que usa la aplicación. Nunca debe
 * incluir detalles internos (nombres de clases, consultas, trazas) ni valores enviados.</p>
 */
public final class Problemas {

    /** Nombre de la propiedad con la lista de errores por campo. */
    public static final String ERRORES = "errores";

    /** Nombre de la propiedad con el código que precisa qué regla se incumplió. */
    public static final String CODIGO = "codigo";

    private static final URI TIPO_VALIDACION = URI.create("/problemas/validacion");
    private static final URI TIPO_NO_AUTENTICADO = URI.create("/problemas/no-autenticado");
    private static final URI TIPO_DEMASIADAS_SOLICITUDES = URI.create("/problemas/demasiadas-solicitudes");
    private static final URI TIPO_INTERNO = URI.create("/problemas/interno");

    private Problemas() {
    }

    /**
     * Crea el error de datos de entrada inválidos, con código HTTP 400.
     *
     * @param detalle explicación general para la persona
     * @param errores problemas campo a campo; puede ir vacía si el fallo no es de un campo
     *                concreto (por ejemplo, un cuerpo que no se puede leer)
     * @return el error listo para devolverse
     */
    public static ProblemDetail validacion(String detalle, List<ErrorCampo> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problema.setType(TIPO_VALIDACION);
        problema.setTitle("Datos de entrada inválidos");
        if (!errores.isEmpty()) {
            problema.setProperty(ERRORES, List.copyOf(errores));
        }
        return problema;
    }

    /**
     * Crea el error de falta de autenticación, con código HTTP 401.
     *
     * <p>Cuándo se usa: cuando la petición no trae un token válido (ausente, caducado, mal
     * firmado, de otro emisor...) y cuando unas credenciales no sirven al iniciar sesión. En
     * ningún caso la explicación debe decir el motivo concreto: así no da pistas a quien
     * prueba tokens o contraseñas.</p>
     *
     * @param detalle explicación genérica para la persona, la misma para todos los motivos
     * @return el error listo para devolverse
     */
    public static ProblemDetail noAutenticado(String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detalle);
        problema.setType(TIPO_NO_AUTENTICADO);
        problema.setTitle("Se requiere autenticación");
        return problema;
    }

    /**
     * Crea el error de demasiadas solicitudes, con código HTTP 429.
     *
     * <p>Quien lo use debe añadir a la respuesta la cabecera {@code Retry-After} con los
     * segundos que hay que esperar.</p>
     *
     * @param detalle explicación para la persona
     * @return el error listo para devolverse
     */
    public static ProblemDetail demasiadasSolicitudes(String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detalle);
        problema.setType(TIPO_DEMASIADAS_SOLICITUDES);
        problema.setTitle("Demasiadas solicitudes");
        return problema;
    }

    /**
     * Crea el error interno, con código HTTP 500.
     *
     * <p>No recibe ningún dato a propósito: la causa real se escribe en el log del servidor
     * y la respuesta solo dice que algo falló.</p>
     *
     * @return el error listo para devolverse
     */
    public static ProblemDetail interno() {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.");
        problema.setType(TIPO_INTERNO);
        problema.setTitle("Error interno");
        return problema;
    }
}
