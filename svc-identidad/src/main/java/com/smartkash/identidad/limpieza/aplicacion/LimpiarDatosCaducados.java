package com.smartkash.identidad.limpieza.aplicacion;

import com.smartkash.identidad.limpieza.puertos.AlmacenDeDatosCaducados;
import com.smartkash.identidad.sesion.dominio.LimiteDeIntentos;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Borra los datos de acceso que ya cumplieron su tiempo de conservación.
 *
 * <p>Qué se borra y cuándo (los plazos son los del modelo de datos):</p>
 * <ul>
 *   <li><b>Contadores de intentos fallidos:</b> 24 horas después del último fallo. El
 *       contador no guarda la hora exacta del último fallo, solo el inicio de su ventana y el
 *       fin de su bloqueo. Un fallo solo se cuenta mientras la ventana sigue abierta, es decir,
 *       hasta 15 minutos después de la más reciente de esas dos horas. Por eso el límite se
 *       adelanta esos 15 minutos: así nunca se borra un contador antes de que pasen 24 horas
 *       desde su último fallo. Para entonces el contador ya no frena a nadie, de modo que
 *       borrarlo no cambia ningún resultado del inicio de sesión.</li>
 *   <li><b>Tokens de renovación:</b> 7 días después de caducar. Un token caducado ya no
 *       sirve; los 7 días permiten investigar un incidente reciente.</li>
 *   <li><b>Enlaces de un solo uso</b> (verificación del correo y recuperación de la
 *       contraseña): 7 días después de caducar, se hayan usado o no. El tope de reenvíos solo
 *       mira la última hora, así que borrarlos no lo afecta.</li>
 * </ul>
 *
 * <p>Por qué existe: estas tablas crecen sin parar, sobre todo la de contadores, que guarda
 * una fila por cada correo inventado que alguien prueba. Además, conservar datos más tiempo
 * del necesario es un riesgo de privacidad aunque estén seudonimizados.</p>
 *
 * <p>Si un borrado falla, los demás se hacen igualmente y al final se lanza el primer error,
 * con los siguientes adjuntos ({@link Throwable#getSuppressed()}). Una avería en una tabla no
 * deja las otras sin limpiar, y el error no se pierde. No hace falta reintentar aquí: la
 * siguiente ejecución recoge lo pendiente.</p>
 *
 * <p>La hora sale del {@link Clock} del servicio, nunca de {@code Instant.now()}, para poder
 * probar los plazos con un reloj parado.</p>
 */
public class LimpiarDatosCaducados {

    /** Tiempo que se conserva un contador desde su último fallo. */
    static final Duration CONSERVACION_CONTADORES = Duration.ofHours(24);

    /** Tiempo que se conserva un token o un enlace después de caducar. */
    static final Duration CONSERVACION_TRAS_CADUCAR = Duration.ofDays(7);

    /** Lo más tarde que puede llegar el último fallo tras la última hora guardada. */
    private static final Duration VENTANA_MAS_LARGA = mayor(
            LimiteDeIntentos.POR_CUENTA.ventana(), LimiteDeIntentos.POR_IP.ventana());

    private final AlmacenDeDatosCaducados almacen;
    private final Clock reloj;

    /**
     * Crea el caso de uso.
     *
     * @param almacen donde se borran los datos
     * @param reloj   de donde sale la hora actual
     */
    public LimpiarDatosCaducados(AlmacenDeDatosCaducados almacen, Clock reloj) {
        this.almacen = almacen;
        this.reloj = reloj;
    }

    /**
     * Borra todo lo caducado.
     *
     * @return cuántas filas se borraron de cada tipo
     * @throws RuntimeException el primer error si algún borrado falló, después de intentar
     *                          todos
     */
    public ResultadoDeLimpieza ejecutar() {
        Instant ahora = reloj.instant();
        Instant limiteContadores = ahora.minus(CONSERVACION_CONTADORES).minus(VENTANA_MAS_LARGA);
        Instant limiteTokens = ahora.minus(CONSERVACION_TRAS_CADUCAR);

        Errores errores = new Errores();
        long contadores = errores.intentar(() -> almacen.borrarContadoresSinActividadDesde(limiteContadores));
        long renovacion = errores.intentar(() -> almacen.borrarTokensDeRenovacionCaducadosAntesDe(limiteTokens));
        long enlaces = errores.intentar(() -> almacen.borrarEnlacesDeUnSoloUsoCaducadosAntesDe(limiteTokens));
        errores.lanzarSiHubo();

        return new ResultadoDeLimpieza(contadores, renovacion, enlaces);
    }

    private static Duration mayor(Duration una, Duration otra) {
        return una.compareTo(otra) >= 0 ? una : otra;
    }

    /* Ejecuta cada borrado y guarda el primer error, con los demás adjuntos. */
    private static final class Errores {

        private RuntimeException primero;

        long intentar(LongSupplier borrado) {
            try {
                return borrado.getAsLong();
            } catch (RuntimeException error) {
                if (primero == null) {
                    primero = error;
                } else {
                    primero.addSuppressed(error);
                }
                return 0;
            }
        }

        void lanzarSiHubo() {
            if (primero != null) {
                throw primero;
            }
        }
    }
}
