package com.smartkash.identidad.verificacion.adaptadores.persistencia;

import com.smartkash.identidad.verificacion.dominio.TokenVerificacion;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Guarda y consulta en PostgreSQL los tokens de los enlaces de verificación del correo.
 *
 * <p>Qué es: el adaptador del puerto {@link RepositorioTokensDeUnUso}. Usa la tabla de tokens
 * de un solo uso, siempre filtrando por el tipo de verificación del correo: los tokens de
 * recuperación de contraseña, cuando existan, no se mezclan con estos.</p>
 *
 * <p>Por qué SQL escrito a mano con {@code JdbcClient}: casi todas las operaciones son
 * condicionales (marcar solo si sigue sin usar, invalidar solo los pendientes) y se expresan
 * mejor y de forma atómica en una sola sentencia.</p>
 *
 * <p>Invalidar los enlaces pendientes anota en ellos el momento de uso. Así dejan de valer por
 * la misma regla que un enlace usado, y su fecha de caducidad no cambia.</p>
 */
@Repository
class RepositorioTokensDeUnUsoPostgres implements RepositorioTokensDeUnUso {

    private static final String TIPO = "VERIFICACION_CORREO";

    private final JdbcClient jdbc;

    /**
     * @param jdbc cliente de SQL; lo entrega Spring
     */
    RepositorioTokensDeUnUsoPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Guarda un token recién emitido.
     *
     * @param token el token, con la huella del valor
     */
    @Override
    public void guardar(TokenVerificacion token) {
        jdbc.sql("""
                        INSERT INTO tokens_un_solo_uso (id, usuario_id, tipo, hash_token, expira_en, usado_en, creado_en)
                        VALUES (:id, :usuarioId, :tipo, :huella, :expiraEn, :usadoEn, :creadoEn)
                        """)
                .param("id", token.id())
                .param("usuarioId", token.usuarioId())
                .param("tipo", TIPO)
                .param("huella", token.huella())
                .param("expiraEn", momento(token.expiraEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("usadoEn", momento(token.usadoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("creadoEn", momento(token.creadoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
    }

    /**
     * Busca el token que corresponde a una huella.
     *
     * @param huella huella del valor que llegó en el enlace
     * @return el token, o una caja vacía si no existe
     */
    @Override
    public Optional<TokenVerificacion> buscarPorHuella(String huella) {
        return jdbc.sql("""
                        SELECT id, usuario_id, hash_token, creado_en, expira_en, usado_en
                        FROM tokens_un_solo_uso
                        WHERE hash_token = :huella AND tipo = :tipo
                        """)
                .param("huella", huella)
                .param("tipo", TIPO)
                .query(RepositorioTokensDeUnUsoPostgres::aToken)
                .optional();
    }

    /**
     * Anota el uso solo si el token sigue sin usar y sin caducar, en una única sentencia.
     *
     * <p>Por qué es seguro con peticiones simultáneas: PostgreSQL bloquea la fila mientras la
     * actualiza. Si llega otra actualización de la misma fila, espera y, al seguir, vuelve a
     * comprobar la condición; como el token ya tiene momento de uso, no actualiza nada y
     * devuelve 0 filas.</p>
     *
     * @param usado el token con el momento de uso anotado
     * @return {@code true} si esta llamada lo marcó
     */
    @Override
    public boolean marcarComoUsado(TokenVerificacion usado) {
        int marcadas = jdbc.sql("""
                        UPDATE tokens_un_solo_uso
                        SET usado_en = :usadoEn
                        WHERE id = :id AND tipo = :tipo AND usado_en IS NULL AND expira_en > :usadoEn
                        """)
                .param("usadoEn", momento(usado.usadoEn()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", usado.id())
                .param("tipo", TIPO)
                .update();
        return marcadas == 1;
    }

    /**
     * Cuenta los tokens emitidos a una cuenta desde un momento dado.
     *
     * @param usuarioId la cuenta
     * @param desde     momento desde el que se cuenta
     * @return cuántos se emitieron
     */
    @Override
    public int contarEmitidosDesde(UUID usuarioId, Instant desde) {
        return jdbc.sql("""
                        SELECT count(*) FROM tokens_un_solo_uso
                        WHERE usuario_id = :usuarioId AND tipo = :tipo AND creado_en >= :desde
                        """)
                .param("usuarioId", usuarioId)
                .param("tipo", TIPO)
                .param("desde", momento(desde), Types.TIMESTAMP_WITH_TIMEZONE)
                .query(Integer.class)
                .single();
    }

    /**
     * Hace que dejen de valer los tokens de una cuenta que siguen sin usar.
     *
     * @param usuarioId la cuenta
     * @param ahora     momento que se anota como uso
     */
    @Override
    public void invalidarPendientes(UUID usuarioId, Instant ahora) {
        jdbc.sql("""
                        UPDATE tokens_un_solo_uso
                        SET usado_en = :ahora
                        WHERE usuario_id = :usuarioId AND tipo = :tipo AND usado_en IS NULL
                        """)
                .param("ahora", momento(ahora), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("usuarioId", usuarioId)
                .param("tipo", TIPO)
                .update();
    }

    /* Convierte una fila en la clase de negocio. */
    private static TokenVerificacion aToken(ResultSet fila, int numero) throws SQLException {
        return new TokenVerificacion(
                fila.getObject("id", UUID.class),
                fila.getObject("usuario_id", UUID.class),
                fila.getString("hash_token"),
                instante(fila, "creado_en"),
                instante(fila, "expira_en"),
                instante(fila, "usado_en"));
    }

    /* Un instante como fecha con zona horaria UTC, el tipo que entiende el driver de PostgreSQL. */
    private static OffsetDateTime momento(Instant instante) {
        return instante == null ? null : OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    private static Instant instante(ResultSet fila, String columna) throws SQLException {
        OffsetDateTime valor = fila.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
