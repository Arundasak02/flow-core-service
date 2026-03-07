package com.flow.core.service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Servlet filter that decompresses GZIP-encoded request bodies.
 * Required for flow-runtime-agent which sends events with Content-Encoding: gzip.
 */
@Component
@Order(1)
public class GzipRequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String contentEncoding = httpRequest.getHeader("Content-Encoding");

        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            chain.doFilter(new GzipRequestWrapper(httpRequest), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class GzipRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] decompressedBody;

        public GzipRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            try (InputStream gzipStream = new GZIPInputStream(request.getInputStream());
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                gzipStream.transferTo(baos);
                this.decompressedBody = baos.toByteArray();
            }
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(decompressedBody);
            return new ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }

        @Override
        public int getContentLength() {
            return decompressedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return decompressedBody.length;
        }
    }
}

