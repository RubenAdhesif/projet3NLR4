package wot.gateway;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer token on /things/** and /events/**.
 * - operator (gateway.token, default operator-secret): full access
 * - viewer (gateway.viewer-token, default viewer-secret): read-only (GET allowed, PUT/POST/DELETE return 403)
 * Dashboard and static files are public.
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private final String operatorToken;
    private final String viewerToken;

    public TokenFilter(@Value("${gateway.token}") String operatorToken,
                       @Value("${gateway.viewer-token:viewer-secret}") String viewerToken) {
        this.operatorToken = operatorToken;
        this.viewerToken = viewerToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/things") || path.startsWith("/events"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length()).trim();
        } else if (request.getRequestURI().equals("/events/stream")) {
            // EventSource cannot send headers
            token = request.getParameter("token");
        }

        if (token == null || (!token.equals(operatorToken) && !token.equals(viewerToken))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"missing or invalid token\"}");
            return;
        }

        if (token.equals(viewerToken) && !request.getMethod().equalsIgnoreCase("GET")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"error\":\"forbidden: read-only access\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
