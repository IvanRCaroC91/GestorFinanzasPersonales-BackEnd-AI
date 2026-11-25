package com.smartkash.finanzas.cuentas.adaptadores.web;

import com.smartkash.finanzas.cuentas.aplicacion.ConsultarCuentas;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Ruta para consultar las cuentas de la persona autenticada.
 *
 * <p>Qué hace: recibe la petición, toma de ella el identificador de la persona y se lo entrega
 * al caso de uso. No consulta la base de datos ni aplica reglas de negocio: solo traduce entre
 * HTTP y el caso de uso.</p>
 *
 * <p>Regla de seguridad: el dueño de las cuentas se toma del dato {@code sub} del token de
 * acceso, que Spring Security ya verificó (firma, caducidad, emisor y destinatario). Nunca se
 * lee de la dirección ni del cuerpo de la petición, así que nadie puede pedir las cuentas de
 * otra persona.</p>
 *
 * <p>Qué es {@code @AuthenticationPrincipal}: le pide a Spring Security el token que ya
 * validó para esta petición, de donde se lee el identificador.</p>
 */
@RestController
@RequestMapping("/api/v1/cuentas")
class CuentasController {

    private final ConsultarCuentas consultarCuentas;

    /**
     * @param consultarCuentas el caso de uso que lista las cuentas; lo entrega Spring
     */
    CuentasController(ConsultarCuentas consultarCuentas) {
        this.consultarCuentas = consultarCuentas;
    }

    /**
     * Lista las cuentas de la persona autenticada, incluidas las archivadas, ordenadas por
     * nombre.
     *
     * @param token el token de acceso ya verificado; su dato {@code sub} es el dueño
     * @return sus cuentas; una lista vacía si no tiene ninguna
     */
    @GetMapping
    List<CuentaRespuesta> listar(@AuthenticationPrincipal Jwt token) {
        UUID dueno = UUID.fromString(token.getSubject());
        return consultarCuentas.consultarCuentasUsuario(dueno).stream()
                .map(CuentaRespuesta::de)
                .toList();
    }
}