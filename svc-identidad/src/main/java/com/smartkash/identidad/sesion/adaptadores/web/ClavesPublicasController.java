package com.smartkash.identidad.sesion.adaptadores.web;

import com.smartkash.identidad.sesion.adaptadores.seguridad.ClaveDeFirma;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publica las claves públicas con las que se comprueban los tokens de acceso.
 *
 * <p>Para qué sirve: los demás servicios descargan estas claves y con ellas validan por su
 * cuenta los tokens que reciben, sin llamar a este servicio en cada petición. Con una clave
 * pública se puede comprobar una firma, pero no fabricarla.</p>
 *
 * <p>De dónde vienen los datos: de la clave de firma que el servicio cargó al arrancar. A
 * dónde van: a {@code GET /.well-known/jwks.json}, una ruta pública pensada para la red
 * interna. {@code /.well-known/} es el prefijo estándar para este tipo de información.</p>
 *
 * <p>Regla: nunca incluye la parte privada; lo garantiza {@link ClaveDeFirma#conjuntoPublico}.
 * El contenido se calcula una sola vez, porque la clave no cambia mientras el servicio está en
 * marcha.</p>
 */
@RestController
class ClavesPublicasController {

    private final Map<String, Object> conjuntoPublico;

    /**
     * @param clave la clave de firma del servicio; la entrega Spring
     */
    ClavesPublicasController(ClaveDeFirma clave) {
        this.conjuntoPublico = clave.conjuntoPublico();
    }

    /**
     * Devuelve las claves públicas en formato JWKS.
     *
     * @return un objeto JSON con la lista {@code keys}
     */
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> claves() {
        return conjuntoPublico;
    }
}