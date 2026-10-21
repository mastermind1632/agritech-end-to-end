package tut.ac.za.AgriFinanceAPIs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public record TokenInfo(String subject, String role) { }

    public JwtService(@Value("${app.security.jwt-secret}") String secret,
                      @Value("${app.security.jwt-expiration-seconds:86400}") long expirationSeconds) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("app.security.jwt-secret (env APP_JWT_SECRET) must contain at least 32 characters");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(String farmerId, String contact, String role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(farmerId).claim("contact", contact).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key).compact();
    }

    /** Kept so any older caller still compiles; new tokens default to FARMER. */
    public String createToken(String farmerId, String contact) {
        return createToken(farmerId, contact, "FARMER");
    }

    public TokenInfo parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        String role = claims.get("role", String.class);
        // Known roles only; anything else (or an old token without a role) is a plain farmer.
        return new TokenInfo(claims.getSubject(), tut.ac.za.AgriFinanceAPIs.common.Roles.normalise(role));
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public String subject(String token) {
        return parse(token).subject();
    }
}
