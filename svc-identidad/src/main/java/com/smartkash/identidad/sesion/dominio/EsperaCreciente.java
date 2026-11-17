package com.smartkash.identidad.sesion.dominio;

import java.time.Duration;

/**
 * Calcula cuánto dura un bloqueo según cuántas veces se ha insistido.
 *
 * <p>Qué hace: al alcanzar el límite de fallos, la espera es de 1 minuto. Cada fallo
 * adicional la duplica (2, 4, 8 minutos) hasta un tope de 15 minutos, que ya no se supera.</p>
 *
 * <p>Por qué crece: una espera fija y corta apenas molesta a un programa que prueba
 * contraseñas sin descanso; una espera fija y larga castiga demasiado a la persona que
 * simplemente se equivocó. Empezar corto y duplicar deja pasar el despiste y frena la
 * insistencia.</p>
 *
 * <p>Por qué tiene tope: cualquiera puede provocar el bloqueo de una cuenta ajena fallando
 * a propósito con su correo. El tope limita cuánto tiempo puede dejar fuera a su dueño.</p>
 *
 * <p>No guarda datos: solo tiene un método de cálculo, por eso no se pueden crear objetos
 * de esta clase.</p>
 */
public final class EsperaCreciente {

    /** Espera al alcanzar el límite de fallos. */
    static final Duration INICIAL = Duration.ofMinutes(1);

    /** Espera máxima, por muchos fallos que se acumulen. */
    static final Duration TOPE = Duration.ofMinutes(15);

    private EsperaCreciente() {
    }

    /**
     * Devuelve la espera que corresponde a un fallo.
     *
     * @param fallosDeMas cuántos fallos hay por encima del límite: 0 para el fallo que
     *                    alcanza el límite, 1 para el siguiente, y así sucesivamente
     * @return 1 minuto duplicado tantas veces como fallos de más haya, sin pasar de 15 minutos
     * @throws IllegalArgumentException si la cantidad es negativa
     */
    public static Duration tras(int fallosDeMas) {
        if (fallosDeMas < 0) {
            throw new IllegalArgumentException("Los fallos de más no pueden ser negativos.");
        }

        // Se duplica paso a paso y se para en cuanto se alcanza el tope. Así el cálculo
        // nunca maneja números enormes, aunque la cantidad de fallos sea gigantesca.
        Duration espera = INICIAL;
        for (int i = 0; i < fallosDeMas && espera.compareTo(TOPE) < 0; i++) {
            espera = espera.multipliedBy(2);
        }
        return espera.compareTo(TOPE) > 0 ? TOPE : espera;
    }
}