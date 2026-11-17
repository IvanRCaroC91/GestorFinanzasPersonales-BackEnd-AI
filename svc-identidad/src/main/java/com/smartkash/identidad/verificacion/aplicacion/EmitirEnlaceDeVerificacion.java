package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.dominio.Usuario;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import com.smartkash.identidad.verificacion.dominio.CorreoDeVerificacion;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Caso de uso: emitir un enlace de verificación nuevo para una cuenta y enviarlo por correo.
 *
 * <p>Quién lo usa: el reenvío, y más adelante el registro, justo después de crear la cuenta.
 * No decide si hay que emitir el enlace (eso lo hace quien llama); solo lo emite.</p>
 *
 * <p>Qué hace, en orden:</p>
 * <ol>
 *   <li>Genera un valor aleatorio y calcula su huella.</li>
 *   <li>En una sola transacción, invalida los enlaces anteriores sin usar y guarda el nuevo.
 *       Así solo funciona el último enlace enviado.</li>
 *   <li>Fuera de la transacción, envía el correo con el valor en claro.</li>
 * </ol>
 *
 * <p>Por qué el correo va fuera de la transacción: enviar un correo no se puede deshacer. Si se
 * enviara dentro y la transacción fallara después, la persona recibiría un enlace que no existe.
 * Al revés, si el envío falla, el enlace queda guardado pero sin enviar: la persona puede pedir
 * otro.</p>
 *
 * <p>El valor en claro solo existe en memoria durante esta llamada: se guarda su huella y se
 * envía por correo.</p>
 */
public class EmitirEnlaceDeVerificacion {

    private final RepositorioTokensDeUnUso tokens;
    private final GeneradorTokens generador;
    private final CalculadorHuellas huellas;
    private final EnviadorCorreos enviador;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;
    private final String direccionBase;

    /**
     * Crea el caso de uso con todo lo que necesita del exterior.
     *
     * @param tokens          dónde se guardan los enlaces
     * @param generador       quién produce el valor aleatorio
     * @param huellas         quién calcula su huella
     * @param enviador        quién envía el correo
     * @param unidadDeTrabajo quién agrupa las escrituras en una transacción
     * @param reloj           de dónde se toma la hora
     * @param direccionBase   dirección de la web a la que apunta el enlace
     * @throws NullPointerException si falta alguno
     */
    public EmitirEnlaceDeVerificacion(
            RepositorioTokensDeUnUso tokens,
            GeneradorTokens generador,
            CalculadorHuellas huellas,
            EnviadorCorreos enviador,
            UnidadDeTrabajo unidadDeTrabajo,
            Clock reloj,
            String direccionBase) {
        this.tokens = Objects.requireNonNull(tokens);
        this.generador = Objects.requireNonNull(generador);
        this.huellas = Objects.requireNonNull(huellas);
        this.enviador = Objects.requireNonNull(enviador);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
        this.direccionBase = Objects.requireNonNull(direccionBase);
    }

    /**
     * Emite un enlace nuevo para la cuenta y se lo envía por correo.
     *
     * @param cuenta la cuenta cuyo correo se verificará
     * @throws RuntimeException si el correo no se pudo enviar; el enlace ya quedó guardado
     */
    public void emitir(Usuario cuenta) {
        Objects.requireNonNull(cuenta, "La cuenta es obligatoria.");
        Instant ahora = reloj.instant();
        String valor = generador.generar();
        TokenVerificacion token = TokenVerificacion.nuevo(cuenta.id(), huellas.huellaDe(valor), ahora);

        unidadDeTrabajo.ejecutar(() -> {
            tokens.invalidarPendientes(cuenta.id(), ahora);
            tokens.guardar(token);
        });

        enviador.enviarVerificacion(CorreoDeVerificacion.para(cuenta, direccionBase, valor));
    }
}