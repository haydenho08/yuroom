package dev.yuroom.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Browser-side defence in depth, on every response.
 *
 * <ul>
 *   <li><b>Content-Security-Policy</b>: only our own scripts, styles and API. If someone ever got
 *       markup into a page, the browser still would not run injected inline script or send data
 *       elsewhere. {@code img-src data:} is for the inline SVG favicon.</li>
 *   <li><b>Referrer-Policy: no-referrer</b>: links out of the app don't tell other sites which
 *       room page someone was on.</li>
 *   <li><b>Permissions-Policy</b>: location only for our own origin (Near me); camera and mic off.</li>
 *   <li><b>HSTS</b> only on HTTPS requests (as seen through the trusted proxy), so local http works.</li>
 * </ul>
 */
@Component
class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "connect-src 'self'",
            "font-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }
        chain.doFilter(request, response);
    }
}
