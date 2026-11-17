package com.smartkash.identidad.verificacion.adaptadores.eventos;

import com.smartkash.identidad.cuenta.dominio.CuentaRegistrada;
import com.smartkash.identidad.verificacion.aplicacion.EmitirEnlaceDeVerificacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envía el primer enlace de verificación cuando se registra una cuenta nueva.
 *
 * <p>Cuándo actúa: {@code @TransactionalEventListener} con la fase {@code AFTER_COMMIT} hace que
 * Spring llame a este método solo cuando la transacción del registro se haya confirmado. Si el
 * registro falla y se deshace, la cuenta no existe y no se envía nada.</p>
 *
 * <p>En segundo plano: {@code @Async} ejecuta el método en otro hilo, así que la respuesta del
 * registro no espera al servidor de correo. Así se cumple que el registro responda sin esperar
 * al correo, y el tiempo de respuesta no delata si el correo era nuevo. Como el servicio usa
 * hilos virtuales, Spring Boot ejecuta estas tareas en hilos virtuales sin más
 * configuración.</p>
 *
 * <p>{@code @EnableAsync} activa {@code @Async} en todo el servicio. Se declara aquí porque esta
 * es la clase que lo necesita.</p>
 *
 * <p>Si el envío falla: la cuenta ya existe y la persona puede pedir otro enlace con el reenvío.
 * Se deja un aviso en el registro del servidor con el identificador de la cuenta y el tipo de
 * error, nunca con el correo ni el enlace.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class PrimerEnlaceAlRegistrarse {

    private static final Logger LOG = LoggerFactory.getLogger(PrimerEnlaceAlRegistrarse.class);

    private final EmitirEnlaceDeVerificacion emitirEnlace;

    /**
     * @param emitirEnlace el caso de uso que emite y envía el enlace; lo entrega Spring
     */
    PrimerEnlaceAlRegistrarse(EmitirEnlaceDeVerificacion emitirEnlace) {
        this.emitirEnlace = emitirEnlace;
    }

    /**
     * Emite y envía el primer enlace de la cuenta recién registrada.
     *
     * @param evento la cuenta registrada
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alRegistrarse(CuentaRegistrada evento) {
        try {
            emitirEnlace.emitir(evento.cuenta());
        } catch (RuntimeException error) {
            LOG.warn("No se pudo enviar el primer enlace de verificación de la cuenta {}: {}",
                    evento.cuenta().id(), error.getClass().getSimpleName());
        }
    }
}