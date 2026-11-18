package com.smartkash.identidad.sesion.adaptadores.seguridad;

import com.smartkash.nucleo.web.errores.Problemas;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Rechaza las peticiones cuyo cuerpo supera un tamaño máximo, antes de que nadie lo procese.
 *
 * <p>Qué problema evita: sin tope, cualquiera podría enviar cuerpos de muchos megas. El servidor
 * los recibiría enteros y el conversor JSON los recorrería, gastando memoria y tiempo en
 * peticiones que nunca serán válidas. Repetido muchas veces, eso basta para dejar el servicio
 * sin recursos. Los cuerpos válidos de este servicio miden menos de 2 KiB.</p>
 *
 * <p>Qué es un filtro: una pieza por la que pasa cada petición antes de llegar al controlador.
 * Puede dejarla seguir ({@code cadena.doFilter}) o cortarla y responder él mismo. Este va el
 * primero de la cadena de seguridad (ver {@link ConfiguracionDeSeguridad}), así que un cuerpo
 * enorme se rechaza sin gastar tiempo ni en comprobar la firma del token.</p>
 *
 * <p>Cómo mide el cuerpo, según cómo lo envíe el cliente:</p>
 * <ul>
 *   <li><b>Con su tamaño declarado</b> (cabecera {@code Content-Length}): si declara más del
 *       tope, se rechaza sin leer ni un byte. Si declara menos, pasa tal cual: el servidor web
 *       no entrega más bytes de los declarados.</li>
 *   <li><b>A trozos, sin declarar el tamaño</b> ({@code Transfer-Encoding: chunked}): se lee
 *       como mucho el tope más un byte. Si hay ese byte de más, se rechaza. Si no, el cuerpo
 *       ya leído se guarda y se entrega al controlador como si nadie lo hubiera tocado.</li>
 * </ul>
 *
 * <p>Qué responde: {@code 400} con el error de validación estándar (RFC 9457). El código HTTP
 * específico sería {@code 413}, pero el contrato de la API no lo contempla y sí contempla el
 * {@code 400} en todas las rutas con cuerpo. La respuesta la escribe Spring MVC: el filtro le
 * pasa el error a través de su mecanismo de manejo de errores, así el formato y el conversor
 * JSON son los mismos que en el resto del servicio.</p>
 *
 * <p>Límite conocido: el filtro no sirve para formularios HTML, porque el servidor web lee sus
 * campos por su cuenta y no vería el cuerpo ya leído. El servicio solo acepta JSON.</p>
 */
class TopeDelCuerpo extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TopeDelCuerpo.class);

    /** Explicación que recibe la persona cuando el cuerpo es demasiado grande. */
    static final String DETALLE = "El cuerpo de la solicitud supera el tamaño máximo permitido.";

    private final int tamanoMaximo;
    private final HandlerExceptionResolver resolutor;

    /**
     * Crea el filtro.
     *
     * @param tamanoMaximo el mayor tamaño de cuerpo admitido, en bytes; uno más ya se rechaza
     * @param resolutor    el mecanismo de Spring MVC que convierte un error en su respuesta
     */
    TopeDelCuerpo(int tamanoMaximo, HandlerExceptionResolver resolutor) {
        this.tamanoMaximo = tamanoMaximo;
        this.resolutor = resolutor;
    }

    /**
     * Mide el cuerpo y deja seguir la petición o la rechaza.
     *
     * @param peticion  la petición que llega
     * @param respuesta la respuesta, que se rellena solo si se rechaza
     * @param cadena    el resto de filtros y el controlador
     * @throws ServletException si falla algún filtro posterior
     * @throws IOException      si falla la lectura del cuerpo
     */
    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {

        long declarado = peticion.getContentLengthLong();
        if (declarado > tamanoMaximo) {
            rechazar(peticion, respuesta);
            return;
        }
        if (declarado >= 0) {
            cadena.doFilter(peticion, respuesta);
            return;
        }

        // Sin tamaño declarado: se lee como mucho un byte más que el tope para saber si se pasa.
        byte[] leido = peticion.getInputStream().readNBytes(tamanoMaximo + 1);
        if (leido.length > tamanoMaximo) {
            rechazar(peticion, respuesta);
            return;
        }
        cadena.doFilter(new CuerpoYaLeido(peticion, leido), respuesta);
    }

    private void rechazar(HttpServletRequest peticion, HttpServletResponse respuesta) {
        LOG.info("Petición rechazada por un cuerpo de más de {} bytes", tamanoMaximo);
        ErrorResponseException error = new ErrorResponseException(
                HttpStatus.BAD_REQUEST, Problemas.validacion(DETALLE, List.of()), null);
        resolutor.resolveException(peticion, respuesta, null, error);
    }

    /*
     * La petición original con su cuerpo sustituido por los bytes que el filtro ya leyó. El
     * cuerpo de una petición solo se puede leer una vez; sin esto, el controlador lo
     * encontraría vacío.
     */
    private static final class CuerpoYaLeido extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        CuerpoYaLeido(HttpServletRequest peticion, byte[] cuerpo) {
            super(peticion);
            this.cuerpo = cuerpo;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream origen = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return origen.read();
                }

                @Override
                public int read(byte[] destino, int desde, int cuantos) {
                    return origen.read(destino, desde, cuantos);
                }

                @Override
                public boolean isFinished() {
                    return origen.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener oyente) {
                    throw new UnsupportedOperationException("La lectura asíncrona no está soportada.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String codificacion = getCharacterEncoding();
            Charset juego = codificacion == null ? StandardCharsets.UTF_8 : Charset.forName(codificacion);
            return new BufferedReader(new InputStreamReader(getInputStream(), juego));
        }
    }
}
