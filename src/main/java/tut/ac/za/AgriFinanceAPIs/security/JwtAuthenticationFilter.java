package tut.ac.za.AgriFinanceAPIs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Created by SecurityConfig - intentionally NOT a Spring bean (see note there). */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    public JwtAuthenticationFilter(JwtService jwtService) { this.jwtService = jwtService; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.TokenInfo info = jwtService.parse(header.substring(7));
                var auth = new UsernamePasswordAuthenticationToken(info.subject(), null,
                        AuthorityUtils.createAuthorityList("ROLE_" + info.role()));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // invalid or expired token: leave the request unauthenticated -> 401 from the entry point
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
