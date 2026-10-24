package tut.ac.za.AgriFinanceAPIs.farmer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.RegisterRequest;
import tut.ac.za.AgriFinanceAPIs.privacy.PrivacyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmerServiceTest {
    private FarmerRepository farmers;
    private PrivacyService privacy;
    private AuditService audit;
    private FarmerService service;

    @BeforeEach
    void setUp() {
        farmers = mock(FarmerRepository.class);
        privacy = mock(PrivacyService.class);
        audit = mock(AuditService.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Registration123")).thenReturn("encoded-password");
        when(farmers.save(any(Farmer.class))).thenAnswer(invocation -> {
            Farmer farmer = invocation.getArgument(0);
            farmer.setId("registered-farmer");
            return farmer;
        });
        service = new FarmerService(farmers, encoder, mock(RateLimiter.class), audit, privacy);
    }

    private RegisterRequest request() {
        var request = new RegisterRequest();
        request.setName("Release test farmer");
        request.setLocation("Polokwane");
        request.setContact("registration-test@example.invalid");
        request.setPassword("Registration123");
        request.setAcceptTerms(true);
        return request;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "unsupported")
    void registrationDefaultsMissingOrUnsupportedLanguageToEnglish(String language) {
        var request = request();
        if (language != null) request.setLanguage(language);

        assertCompletedRegistration(service.register(request), "en");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "af", "nso", "ts", "zu"})
    void registrationPreservesEachSupportedLanguage(String language) {
        var request = request();
        request.setLanguage(language);

        assertCompletedRegistration(service.register(request), language);
    }

    @Test
    void legacyRegistrationWithoutLanguageStillCompletes() {
        assertCompletedRegistration(service.register("Release test farmer", "Polokwane",
                "registration-test@example.invalid", "Registration123"), "en");
    }

    private void assertCompletedRegistration(Farmer farmer, String expectedLanguage) {
        assertThat(farmer.getId()).isEqualTo("registered-farmer");
        assertThat(farmer.getPreferredLanguage()).isEqualTo(expectedLanguage);
        assertThat(farmer.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(farmer.getRole()).isEqualTo("FARMER");
        verify(farmers).save(farmer);
        verify(privacy).record("registered-farmer", "TERMS", true);
        verify(privacy).record("registered-farmer", "PRIVACY", true);
        verify(audit).record("registered-farmer", "REGISTERED", "FARMER", "registered-farmer", "FARMER");
    }
}
