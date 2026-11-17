package com.smartkash.identidad.registro.adaptadores.eventos;

import com.smartkash.identidad.cuenta.dominio.CuentaRegistrada;
import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.registro.puertos.AvisoDeCuentaNueva;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Avisa de una cuenta nueva publicando el evento {@link CuentaRegistrada}.
 *
 * <p>Qué es: el adaptador del puerto {@link AvisoDeCuentaNueva}. Usa
 * {@code ApplicationEventPublisher}, el mecanismo de Spring para publicar eventos dentro del
 * servicio. Publicar no hace nada por sí mismo: lo que ocurra depende de quién escuche.</p>
 *
 * <p>Los escuchadores pueden pedir actuar solo cuando la transacción en curso se confirme;
 * por eso este adaptador no necesita esperar a nada.</p>
 */
@Component
class AvisoDeCuentaNuevaConEventos implements AvisoDeCuentaNueva {

    private final ApplicationEventPublisher publicador;

    /**
     * @param publicador el publicador de eventos de Spring; lo entrega Spring
     */
    AvisoDeCuentaNuevaConEventos(ApplicationEventPublisher publicador) {
        this.publicador = publicador;
    }

    /**
     * Publica el evento de cuenta registrada.
     *
     * @param cuenta la cuenta recién guardada
     */
    @Override
    public void cuentaCreada(Usuario cuenta) {
        publicador.publishEvent(new CuentaRegistrada(cuenta));
    }
}