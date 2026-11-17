package com.smartkash.identidad.sesion.puertos;

import java.time.Instant;
import java.util.UUID;

/**
 * Pregunta si una cuenta tiene pendiente aceptar una política de tratamiento de datos.
 *
 * <p>Qué es: un puerto. El caso de uso de inicio de sesión lo consulta al dejar entrar a una
 * persona; la respuesta viaja al frontend, que le pedirá aceptar la política nueva.</p>
 *
 * <p>Regla: hay una política pendiente si se publicó (con fecha de publicación no posterior a
 * {@code ahora}) una versión que cambia la finalidad del tratamiento y la cuenta no ha
 * aceptado ni esa versión ni ninguna publicada después. Aceptar una versión posterior cuenta
 * como aceptar las anteriores. Una autorización rechazada no cuenta como aceptada.</p>
 */
@FunctionalInterface
public interface ConsultaDePoliticas {

    /**
     * Dice si la cuenta tiene una política pendiente de aceptar.
     *
     * @param usuarioId la cuenta, ya autenticada
     * @param ahora     momento de la consulta; las políticas con fecha posterior no cuentan
     * @return {@code true} si debe aceptar una política nueva
     */
    boolean hayPoliticaPendiente(UUID usuarioId, Instant ahora);
}