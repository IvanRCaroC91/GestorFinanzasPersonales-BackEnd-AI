package com.smartkash.identidad.verificacion.aplicacion;

import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.verificacion.dominio.EnlaceNoValido;
import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Caso de uso: confirmar el correo de una persona con el enlace que se le envió.
 *
 * <p>Qué hace, en orden:</p>
 * <ol>
 *   <li>Calcula la huella del valor recibido y busca el token por ella.</li>
 *   <li>Comprueba con el dominio que se puede usar: existe, no caducó y no se usó.</li>
 *   <li>Lo marca como usado y activa la cuenta, las dos cosas en una sola unidad de trabajo:
 *       se guardan juntas o no se guarda ninguna.</li>
 * </ol>
 *
 * <p>De dónde vienen los datos: el valor del token llega de la capa web, que lo recibe en el
 * cuerpo de la petición; la persona lo sacó del enlace del correo. A dónde van: a la tabla de
 * tokens (momento de uso) y a la de cuentas (estado y fecha de verificación).</p>
 *
 * <p>Quién es el dueño: el token, no la petición. La cuenta que se activa es la que se guardó
 * con el token al emitirlo; la petición no puede elegir otra.</p>
 *
 * <p>Errores: {@link EnlaceNoValido} si el enlace no existe, caducó, ya se usó o lo acaba de
 * usar otra petición. Siempre el mismo error, para no dar pistas a quien prueba enlaces.</p>
 *
 * <p>Verificar no inicia sesión: la persona lo hará después con su contraseña.</p>
 */
public class VerificarCorreo {

    private final RepositorioTokensDeUnUso tokens;
    private final RepositorioUsuarios usuarios;
    private final CalculadorHuellas huellas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Clock reloj;

    /**
     * Crea el caso de uso con todo lo que necesita del exterior.
     *
     * @param tokens          dónde están los tokens de verificación
     * @param usuarios        dónde se activa la cuenta
     * @param huellas         quién calcula la huella del valor recibido
     * @param unidadDeTrabajo quién agrupa las escrituras en una transacción
     * @param reloj           de dónde se toma la hora; en las pruebas, un reloj detenido
     * @throws NullPointerException si falta alguno
     */
    public VerificarCorreo(
            RepositorioTokensDeUnUso tokens,
            RepositorioUsuarios usuarios,
            CalculadorHuellas huellas,
            UnidadDeTrabajo unidadDeTrabajo,
            Clock reloj) {
        this.tokens = Objects.requireNonNull(tokens);
        this.usuarios = Objects.requireNonNull(usuarios);
        this.huellas = Objects.requireNonNull(huellas);
        this.unidadDeTrabajo = Objects.requireNonNull(unidadDeTrabajo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /**
     * Verifica el correo con el valor del enlace.
     *
     * @param valorToken el valor que llegó en el enlace, tal cual
     * @throws EnlaceNoValido       si el enlace no sirve, por el motivo que sea
     * @throws NullPointerException si falta el valor
     */
    public void ejecutar(String valorToken) {
        Objects.requireNonNull(valorToken, "El valor del enlace es obligatorio.");
        Instant ahora = reloj.instant();
        String huella = huellas.huellaDe(valorToken);

        unidadDeTrabajo.ejecutar(() -> {
            TokenVerificacion token = tokens.buscarPorHuella(huella).orElseThrow(EnlaceNoValido::new);
            TokenVerificacion usado = token.usar(ahora);

            // Otra petición pudo usar el mismo enlace entre la búsqueda y este punto. Solo una
            // consigue marcarlo; la que llega tarde recibe el mismo error que un enlace usado.
            if (!tokens.marcarComoUsado(usado)) {
                throw new EnlaceNoValido();
            }
            usuarios.marcarVerificada(token.usuarioId(), ahora);
        });
    }
}
