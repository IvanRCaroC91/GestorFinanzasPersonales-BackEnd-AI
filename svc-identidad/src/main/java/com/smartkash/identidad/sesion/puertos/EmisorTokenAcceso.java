package com.smartkash.identidad.sesion.puertos;

import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.sesion.dominio.TokenAcceso;

import java.time.Instant;

/**
 * Emite el token de acceso de una persona que acaba de demostrar quién es.
 *
 * <p>Qué es: un puerto. El caso de uso de inicio de sesión pide el token sin saber cómo se
 * firma ni con qué clave; eso lo decide la clase que implemente esta interfaz.</p>
 */
@FunctionalInterface
public interface EmisorTokenAcceso {

    /**
     * Emite un token de acceso firmado para una cuenta.
     *
     * <p>Reglas: el token identifica a la cuenta por su identificador y lleva sus roles;
     * no incluye el correo, el nombre ni ningún otro dato personal. Vale durante
     * {@link TokenAcceso#VIGENCIA} a partir de la hora indicada.</p>
     *
     * @param usuario la cuenta que inició sesión
     * @param ahora   momento de la emisión
     * @return el token firmado y su caducidad
     */
    TokenAcceso emitir(Usuario usuario, Instant ahora);
}