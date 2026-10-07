package tut.ac.za.AgriFinanceAPIs.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: the app crashed at start-up with
 * "Could not resolve placeholder 'app.security.jwt-secret'" because the property
 * pointed at an environment variable that had no fallback value.
 * Every ${ENV_VAR} placeholder in application.properties must carry a default.
 */
class ApplicationPropertiesTest {

    private static final Pattern PLACEHOLDER_WITHOUT_DEFAULT = Pattern.compile("\\$\\{[^:}]+}");

    private Properties load() throws Exception {
        Properties props = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            props.load(in);
        }
        return props;
    }

    @Test
    void everyPlaceholderHasADefaultValue() throws Exception {
        Properties props = load();
        for (String key : props.stringPropertyNames()) {
            assertThat(PLACEHOLDER_WITHOUT_DEFAULT.matcher(props.getProperty(key)).find())
                    .as("property '%s' uses a placeholder without a default value", key)
                    .isFalse();
        }
    }

    @Test
    void jwtSecretFallbackIsLongEnough() throws Exception {
        String value = load().getProperty("app.security.jwt-secret");
        assertThat(value).isNotBlank();
        String fallback = value.substring(value.indexOf(':') + 1, value.lastIndexOf('}'));
        assertThat(fallback.length()).isGreaterThanOrEqualTo(32);
    }
}
