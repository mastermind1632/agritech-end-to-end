package tut.ac.za.AgriFinanceAPIs.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-characters-long";
    private static final String OTHER_SECRET = "a-completely-different-secret-with-32-or-more-chars";

    @Test
    void tokenRoundTripReturnsFarmerId() {
        JwtService service = new JwtService(SECRET, 3600);
        String token = service.createToken("farmer-1", "0712345678");
        assertThat(service.subject(token)).isEqualTo("farmer-1");
    }

    @Test
    void rejectsSecretShorterThan32Characters() {
        assertThatThrownBy(() -> new JwtService("too-short", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = new JwtService(OTHER_SECRET, 3600).createToken("farmer-1", "x");
        JwtService service = new JwtService(SECRET, 3600);
        assertThatThrownBy(() -> service.subject(foreign)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(SECRET, -60);
        String expired = service.createToken("farmer-1", "x");
        assertThatThrownBy(() -> service.subject(expired)).isInstanceOf(Exception.class);
    }
}
