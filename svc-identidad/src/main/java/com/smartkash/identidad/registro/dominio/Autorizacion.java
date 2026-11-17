package com.smartkash.identidad.registro.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Prueba de que una persona aceptó (o retiró) su autorización sobre la política de
 * tratamiento de datos personales.
 *
 * <p>Qué hace: deja constancia de quién autorizó, qué versión de la política vio, por
 * qué canal y en qué momento. La ley colombiana de protección de datos personales exige
 * poder demostrarlo.</p>
 *
 * <p>De dónde vienen los datos: del registro de la cuenta (la versión viene del cuerpo
 * de la solicitud; el usuario y el momento los pone el servidor). A dónde van: a la
 * tabla de autorizaciones, que solo admite inserciones: una autorización nunca se
 * modifica; si cambia, se añade otra fila.</p>
 *
 * <p>Reglas y errores: el momento se recibe de fuera, no se calcula aquí, para que las
 * reglas de negocio no dependan del reloj de la máquina y se puedan probar con una fecha
 * fija. Lanza {@link NullPointerException} si falta un dato y
 * {@link IllegalArgumentException} si la versión o el canal están vacíos o son demasiado
 * largos.</p>
 *
 * @param id              identificador de esta prueba
 * @param usuarioId       cuenta de la persona que autoriza
 * @param versionPolitica versión de la política que vio la persona
 * @param otorgada        {@code true} si autoriza; {@code false} si retira la autorización
 * @param canal           medio por el que se dio la autorización (por ejemplo, la web)
 * @param registradaEn    momento exacto en que se dio
 */
public record Autorizacion(
        UUID id,
        UUID usuarioId,
        String versionPolitica,
        boolean otorgada,
        String canal,
        Instant registradaEn) {

    /** Canal usado cuando la persona autoriza desde la aplicación web. */
    public static final String CANAL_WEB = "WEB";

    /** Cantidad máxima de caracteres de la versión de la política y del canal. */
    public static final int LARGO_MAXIMO_TEXTO = 10;

    /**
     * Valida los datos al crear el objeto.
     *
     * @throws NullPointerException     si falta el identificador, el usuario o el momento
     * @throws IllegalArgumentException si la versión o el canal son nulos, están vacíos o
     *                                  superan el largo máximo
     */
    public Autorizacion {
        Objects.requireNonNull(id, "El identificador es obligatorio.");
        Objects.requireNonNull(usuarioId, "El usuario es obligatorio.");
        Objects.requireNonNull(registradaEn, "El momento de la autorización es obligatorio.");
        exigirTextoCorto(versionPolitica, "La versión de la política");
        exigirTextoCorto(canal, "El canal");
    }

    /**
     * Crea la prueba de que una persona aceptó la política desde la web al registrarse.
     *
     * @param usuarioId       cuenta de la persona
     * @param versionPolitica versión de la política que aceptó
     * @param momento         momento de la aceptación, entregado por quien llama
     * @return la autorización otorgada, lista para guardarse
     * @throws NullPointerException     si falta el usuario o el momento
     * @throws IllegalArgumentException si la versión no es válida
     */
    public static Autorizacion aceptacionDePolitica(
            UUID usuarioId, String versionPolitica, Instant momento) {
        return new Autorizacion(
                UUID.randomUUID(), usuarioId, versionPolitica, true, CANAL_WEB, momento);
    }

    /** Comprueba que un texto obligatorio no esté vacío ni supere el largo máximo. */
    private static void exigirTextoCorto(String texto, String nombreDelDato) {
        if (texto == null || texto.isBlank() || texto.length() > LARGO_MAXIMO_TEXTO) {
            throw new IllegalArgumentException(nombreDelDato
                    + " es obligatorio y no puede tener más de " + LARGO_MAXIMO_TEXTO
                    + " caracteres.");
        }
    }
}
