package tut.ac.za.AgriFinanceAPIs.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import tut.ac.za.AgriFinanceAPIs.vault.VaultCrypto;

/** Shared beans: background jobs, async notification delivery, rate limiting, vault encryption and API docs. */
@Configuration
@EnableAsync
@EnableScheduling
public class PlatformConfig {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Bean
    public RateLimiter rateLimiter() {
        return rateLimiter;
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    public void cleanRateLimiter() {
        rateLimiter.cleanup(60 * 60 * 1000L);
    }

    @Bean
    public VaultCrypto vaultCrypto(@Value("${app.vault.key:}") String key,
                                   @Value("${app.security.jwt-secret}") String jwtSecret) {
        return new VaultCrypto(key, jwtSecret);
    }

    /** Swagger UI at /swagger-ui.html, raw spec at /v3/api-docs. Use "Authorize" with a token from /api/farmers/login. */
    @Bean
    public OpenAPI agritechOpenApi() {
        return new OpenAPI()
                .info(new Info().title("AgriTech Platform API").version("2.0")
                        .description("Marketplace, business companion, export readiness, expense analysis and shared platform services."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
