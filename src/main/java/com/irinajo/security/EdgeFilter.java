package com.irinajo.security;

import com.irinajo.common.RateLimiter;
import com.irinajo.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;

/**
 * Runs before Spring Security:
 *  1. HTTPS only (Railway terminates TLS; plain-http requests arrive with X-Forwarded-Proto: http).
 *  2. Per-IP rate limits (stricter on /auth), and per-device limits on the public explore routes.
 *  3. Accepts gzip-compressed request bodies (Content-Encoding: gzip), with a size cap against zip bombs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EdgeFilter extends OncePerRequestFilter {

    private static final long MAX_INFLATED_BYTES = 5L * 1024 * 1024;

    private final AppProperties props;
    private final RateLimiter limiter;

    public EdgeFilter(AppProperties props, RateLimiter limiter) {
        this.props = props;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();

        if (props.requireHttps() && !path.startsWith("/actuator/health")) {
            String proto = req.getHeader("X-Forwarded-Proto");
            if ("http".equalsIgnoreCase(proto)) {
                reject(res, 403, "HTTPS_REQUIRED", "Use HTTPS", false, 0);
                return;
            }
        }

        String ip = ClientIp.of(req);
        boolean auth = path.startsWith("/auth/") || path.startsWith("/account/delete");
        int wait = auth ? limiter.hit("ip-auth:" + ip, 30, 60) : limiter.hit("ip:" + ip, 300, 60);
        if (wait > 0) {
            res.setHeader("Retry-After", String.valueOf(wait));
            reject(res, 429, "RATE_LIMITED", "Too many requests, slow down", true, wait);
            return;
        }

        boolean publicExplore = path.startsWith("/walks/") || path.equals("/routes/suggest");
        String device = req.getHeader("X-Device-Id");
        if (publicExplore && device != null && !device.isBlank()) {
            int w = limiter.hit("dev-explore:" + device, 60, 60);
            if (w > 0) {
                res.setHeader("Retry-After", String.valueOf(w));
                reject(res, 429, "RATE_LIMITED", "Too many requests, slow down", true, w);
                return;
            }
        }

        String enc = req.getHeader("Content-Encoding");
        if (enc != null && enc.toLowerCase().contains("gzip")) {
            chain.doFilter(new GzipRequest(req), res);
        } else {
            chain.doFilter(req, res);
        }
    }

    private void reject(HttpServletResponse res, int status, String code, String msg, boolean retryable, int wait)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + msg
                + "\",\"retryable\":" + retryable + "}}");
    }

    private static final class GzipRequest extends HttpServletRequestWrapper {
        GzipRequest(HttpServletRequest r) { super(r); }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            InputStream gz = new GZIPInputStream(super.getInputStream());
            return new ServletInputStream() {
                long total = 0;
                @Override public int read() throws IOException {
                    int b = gz.read();
                    if (b >= 0 && ++total > MAX_INFLATED_BYTES) throw new IOException("Request body too large");
                    return b;
                }
                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    int n = gz.read(buf, off, len);
                    if (n > 0 && (total += n) > MAX_INFLATED_BYTES) throw new IOException("Request body too large");
                    return n;
                }
                @Override public boolean isFinished() { return false; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
            };
        }

        @Override public int getContentLength() { return -1; }
        @Override public long getContentLengthLong() { return -1; }

        @Override
        public String getHeader(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Content-Encoding".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}