package com.smartkash.identidad.sesion.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de {@link TipoDeContador}.
 *
 * <p>Qué comprueban: que cada tipo de contador usa su límite y que los nombres coinciden con
 * los valores que admite la tabla de contadores de acceso.</p>
 */
class TipoDeContadorTest {

    @Test
    @DisplayName("el contador de una cuenta usa el límite por cuenta y el de una IP, el límite por IP")
    void cadaTipoUsaSuLimite() {
        assertEquals(LimiteDeIntentos.POR_CUENTA, TipoDeContador.CUENTA.limite());
        assertEquals(LimiteDeIntentos.POR_IP, TipoDeContador.IP.limite());
    }

    @Test
    @DisplayName("los nombres son exactamente los que admite la tabla: CUENTA e IP")
    void losNombresCoincidenConLaTabla() {
        List<String> nombres = Arrays.stream(TipoDeContador.values()).map(Enum::name).toList();

        assertEquals(List.of("CUENTA", "IP"), nombres);
    }
}