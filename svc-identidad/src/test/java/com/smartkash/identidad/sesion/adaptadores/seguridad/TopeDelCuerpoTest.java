package com.smartkash.identidad.sesion.adaptadores.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba el filtro que limita el tamaño del cuerpo de las peticiones, solo, sin Spring.
 *
 * <p>Qué hace: crea el filtro con un tope pequeño (100 bytes) y le pasa peticiones simuladas.
 * El resto de la cadena es un sustituto que anota si la petición llegó y qué cuerpo leyó; el
 * mecanismo de errores de Spring MVC es otro sustituto que anota qué error recibió.</p>
 *
 * <p>Hay dos casos porque hay dos formas de enviar un cuerpo: diciendo antes cuánto mide
 * (cabecera {@code Content-Length}) o enviándolo a trozos sin decirlo
 * ({@code Transfer-Encoding: chunked}).</p>
 */
class TopeDelCuerpoTest {

    private static final int TOPE = 100;

    private final List<ErrorResponse> errores = new ArrayList<>();
    private final AtomicReference<byte[]> cuerpoLeido = new AtomicReference<>();
    private final TopeDelCuerpo filtro = new TopeDelCuerpo(TOPE, registrarError());

    // ---------- Con el tamaño declarado ----------

    /** Un cuerpo que declara medir más que el tope se rechaza sin llegar al controlador. */
    @Test
    void un_cuerpo_declarado_mayor_que_el_tope_se_rechaza() throws Exception {
        MockHttpServletRequest peticion = conCuerpo(TOPE + 1);

        filtrar(peticion);

        assertThat(cuerpoLeido.get()).as("no llega al controlador").isNull();
        assertRechazado();
    }

    /**
     * Se rechaza sin leer el cuerpo: lo que manda es el tamaño declarado. Así quien envía un
     * cuerpo enorme no consigue que el servidor lo reciba entero.
     */
    @Test
    void con_el_tamano_declarado_se_rechaza_sin_leer_el_cuerpo() throws Exception {
        CuerpoQueCuenta cuerpo = new CuerpoQueCuenta(bytes(TOPE + 1));

        filtrar(new ConCuerpo(conCuerpo(TOPE + 1), cuerpo));

        assertThat(cuerpo.leido).as("bytes leídos").isZero();
        assertRechazado();
    }

    /** Un cuerpo justo del tamaño del tope pasa y el controlador lo lee completo. */
    @Test
    void un_cuerpo_declarado_igual_al_tope_pasa() throws Exception {
        MockHttpServletRequest peticion = conCuerpo(TOPE);

        filtrar(peticion);

        assertThat(cuerpoLeido.get()).hasSize(TOPE);
        assertThat(errores).isEmpty();
    }

    // ---------- Sin el tamaño declarado (a trozos) ----------

    /** Un cuerpo enviado a trozos que supera el tope se rechaza sin llegar al controlador. */
    @Test
    void un_cuerpo_a_trozos_mayor_que_el_tope_se_rechaza() throws Exception {
        filtrar(aTrozos(TOPE + 1));

        assertThat(cuerpoLeido.get()).as("no llega al controlador").isNull();
        assertRechazado();
    }

    /**
     * Un cuerpo enviado a trozos que cabe en el tope pasa, y el controlador lee exactamente lo
     * que se envió, aunque el filtro ya lo haya leído para medirlo.
     */
    @Test
    void un_cuerpo_a_trozos_dentro_del_tope_pasa_intacto() throws Exception {
        byte[] enviado = bytes(TOPE);

        filtrar(aTrozos(enviado));

        assertThat(cuerpoLeido.get()).isEqualTo(enviado);
        assertThat(errores).isEmpty();
    }

    /** Una petición sin cuerpo pasa. */
    @Test
    void una_peticion_sin_cuerpo_pasa() throws Exception {
        filtrar(new MockHttpServletRequest("GET", "/.well-known/jwks.json"));

        assertThat(cuerpoLeido.get()).isEmpty();
        assertThat(errores).isEmpty();
    }

    // ---------- Ayudas ----------

    private void assertRechazado() {
        assertThat(errores).hasSize(1);
        ProblemDetail problema = errores.getFirst().getBody();
        assertThat(problema.getStatus()).isEqualTo(400);
        assertThat(problema.getType()).hasToString("/problemas/validacion");
        assertThat(problema.getDetail()).isEqualTo(TopeDelCuerpo.DETALLE);
    }

    private void filtrar(HttpServletRequest peticion) throws Exception {
        filtro.doFilter(peticion, new MockHttpServletResponse(), cadena());
    }

    /* Resto de la cadena: lee el cuerpo entero, como haría el conversor JSON del controlador. */
    private FilterChain cadena() {
        return (peticion, respuesta) -> cuerpoLeido.set(peticion.getInputStream().readAllBytes());
    }

    /* Sustituto del mecanismo de errores de Spring MVC: anota el error y responde 400. */
    private HandlerExceptionResolver registrarError() {
        return (peticion, respuesta, manejador, error) -> {
            errores.add((ErrorResponse) error);
            respuesta.setStatus(400);
            return new ModelAndView();
        };
    }

    private static MockHttpServletRequest conCuerpo(int tamano) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/auth/registro");
        peticion.setContentType("application/json");
        peticion.setContent(bytes(tamano));
        return peticion;
    }

    private static HttpServletRequest aTrozos(int tamano) {
        return aTrozos(bytes(tamano));
    }

    /* Una petición que no declara su tamaño: Content-Length vale -1, como al enviar a trozos. */
    private static HttpServletRequest aTrozos(byte[] contenido) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("POST", "/api/v1/auth/registro") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        peticion.setContentType("application/json");
        peticion.addHeader("Transfer-Encoding", "chunked");
        peticion.setContent(contenido);
        return peticion;
    }

    private static byte[] bytes(int tamano) {
        byte[] contenido = new byte[tamano];
        Arrays.fill(contenido, (byte) 'a');
        return contenido;
    }

    /* Cuerpo que cuenta cuántos bytes se le leen. */
    private static final class CuerpoQueCuenta extends InputStream {
        private final InputStream origen;
        private int leido;

        CuerpoQueCuenta(byte[] contenido) {
            this.origen = new ByteArrayInputStream(contenido);
        }

        @Override
        public int read() throws IOException {
            int byteLeido = origen.read();
            if (byteLeido >= 0) {
                leido++;
            }
            return byteLeido;
        }
    }

    /* Envuelve una petición para que su cuerpo sea el indicado. */
    private static final class ConCuerpo extends HttpServletRequestWrapper {
        private final InputStream cuerpo;

        ConCuerpo(HttpServletRequest peticion, InputStream cuerpo) {
            super(peticion);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    return cuerpo.read();
                }

                @Override
                public boolean isFinished() {
                    return false;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener oyente) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
