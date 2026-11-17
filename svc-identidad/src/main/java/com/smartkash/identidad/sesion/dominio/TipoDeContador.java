package com.smartkash.identidad.sesion.dominio;

/**
 * A quién pertenece un contador de inicios de sesión fallidos.
 *
 * <p>Los nombres coinciden exactamente con los valores que admite la columna de tipo en la
 * tabla de contadores de acceso. Cada tipo lleva asociado el límite con el que se cuenta.</p>
 */
public enum TipoDeContador {

    /** Fallos contra una misma cuenta, venga el intento de donde venga. */
    CUENTA(LimiteDeIntentos.POR_CUENTA),

    /** Fallos desde una misma dirección IP, sea cual sea la cuenta. */
    IP(LimiteDeIntentos.POR_IP);

    private final LimiteDeIntentos limite;

    TipoDeContador(LimiteDeIntentos limite) {
        this.limite = limite;
    }

    /**
     * Devuelve el límite que se aplica a este tipo de contador.
     *
     * @return cuántos fallos se toleran y en cuánto tiempo
     */
    public LimiteDeIntentos limite() {
        return limite;
    }
}