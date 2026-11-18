package com.smartkash.identidad.cuenta.adaptadores.seguridad;

import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import com.smartkash.identidad.cuenta.puertos.HasheadorSaturado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limita cuántos cálculos de hash de contraseñas se hacen a la vez; los que no caben esperan
 * un tiempo acotado y, si no llega su turno, se rechazan.
 *
 * <p>Qué problema evita: cada cálculo Argon2 reserva unos 19 MiB de memoria y ocupa un núcleo
 * durante decenas de milisegundos. Como cada petición corre en su propio hilo virtual (hilos
 * muy baratos, que Java crea sin límite práctico), mil inicios de sesión simultáneos
 * pedirían unos 19 GiB a la vez y el servicio se quedaría sin memoria. Con el tope, la memoria
 * máxima es {@code turnos × 19 MiB}, pase lo que pase.</p>
 *
 * <p>Cómo funciona: usa un {@link Semaphore} (semáforo), un contador de turnos. Antes de
 * calcular, cada hilo pide un turno; al terminar, lo devuelve, también si el cálculo falla. Si
 * no hay turnos libres, el hilo espera, en orden de llegada, como mucho {@code esperaMaxima}.
 * Si se le acaba la espera, lanza {@link HasheadorSaturado} sin calcular nada. Por ejemplo,
 * con 4 turnos y 2 segundos: el quinto inicio de sesión simultáneo espera a que acabe
 * cualquiera de los cuatro primeros, cosa que suele tardar milisegundos, y solo se rechaza si
 * en 2 segundos no se ha liberado ninguno.</p>
 *
 * <p>Qué es: un decorador, una clase que implementa la misma interfaz que otra, la envuelve y
 * le añade algo (aquí, el tope) sin cambiarla. Los casos de uso siguen viendo un
 * {@link HasheadorContrasena} y no saben que existe el tope.</p>
 */
class HasheadorConTope implements HasheadorContrasena {

    private static final Logger LOG = LoggerFactory.getLogger(HasheadorConTope.class);

    private final HasheadorContrasena real;
    private final Semaphore semaforo;
    private final int turnos;
    private final Duration esperaMaxima;

    /**
     * Crea el decorador.
     *
     * @param real         el hasheador que hace los cálculos de verdad
     * @param turnos       cuántos cálculos se permiten a la vez; al menos 1
     * @param esperaMaxima cuánto puede esperar turno cada cálculo; cero o más
     * @throws IllegalArgumentException si {@code turnos} es menor que 1 o la espera es negativa
     */
    HasheadorConTope(HasheadorContrasena real, int turnos, Duration esperaMaxima) {
        if (turnos < 1) {
            throw new IllegalArgumentException("Debe haber al menos un turno.");
        }
        if (esperaMaxima.isNegative()) {
            throw new IllegalArgumentException("La espera máxima no puede ser negativa.");
        }
        this.real = real;
        this.turnos = turnos;
        this.esperaMaxima = esperaMaxima;
        // true = justo: los turnos se reparten en orden de llegada, nadie se cuela.
        this.semaforo = new Semaphore(turnos, true);
    }

    /**
     * Calcula el hash cuando hay turno.
     *
     * @param contrasena la contraseña en claro, ya validada
     * @return el hash que devuelve el hasheador real
     * @throws HasheadorSaturado si no hubo turno dentro de la espera máxima
     */
    @Override
    public String hashear(String contrasena) {
        return conTurno(() -> real.hashear(contrasena));
    }

    /**
     * Compara la contraseña con el hash cuando hay turno.
     *
     * @param contrasena la contraseña en claro que escribió la persona
     * @param hash       el hash guardado
     * @return lo que responda el hasheador real
     * @throws HasheadorSaturado si no hubo turno dentro de la espera máxima
     */
    @Override
    public boolean coincide(String contrasena, String hash) {
        return conTurno(() -> real.coincide(contrasena, hash));
    }

    /** @return cuántos cálculos se permiten a la vez */
    int turnos() {
        return turnos;
    }

    /** @return cuánto puede esperar turno cada cálculo */
    Duration esperaMaxima() {
        return esperaMaxima;
    }

    private <T> T conTurno(Supplier<T> calculo) {
        if (!pedirTurno()) {
            LOG.warn("Cálculo de contraseña rechazado: los {} turnos siguen ocupados tras {} ms de espera",
                    turnos, esperaMaxima.toMillis());
            throw new HasheadorSaturado();
        }
        try {
            return calculo.get();
        } finally {
            semaforo.release();
        }
    }

    /*
     * Si el hilo se interrumpe mientras espera (por ejemplo, porque el servidor se está
     * apagando), se trata como falta de turno. La marca de interrupción se vuelve a poner porque
     * capturar la excepción la borra, y quien gestiona el hilo necesita verla.
     */
    private boolean pedirTurno() {
        try {
            return semaforo.tryAcquire(esperaMaxima.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupcion) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
