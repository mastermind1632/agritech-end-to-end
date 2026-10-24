package tut.ac.za.AgriFinanceAPIs.farmer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.RegisterRequest;
import tut.ac.za.AgriFinanceAPIs.privacy.PrivacyService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmerServiceTest {
    private FarmerRepository farmers;
    private PrivacyService privacy;
    private AuditService audit;
    private PasswordEncoder encoder;
    private FarmerService service;

    @BeforeEach
    void setUp() {
        farmers = mock(FarmerRepository.class);
        privacy = mock(PrivacyService.class);
        audit = mock(AuditService.class);
        encoder = mock(PasswordEncoder.class);
        when(encoder.encode("Registration123")).thenReturn("encoded-password");
        when(farmers.save(any(Farmer.class))).thenAnswer(invocation -> {
            Farmer farmer = invocation.getArgument(0);
            farmer.setId("registered-farmer");
            return farmer;
        });
        service = new FarmerService(farmers, encoder, new RateLimiter(), audit, privacy);
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

    private Farmer loginAccount(String id, String contact, String email, String passwordHash) {
        var farmer = new Farmer();
        farmer.setId(id);
        farmer.setContact(contact);
        farmer.setEmail(email);
        farmer.setPasswordHash(passwordHash);
        return farmer;
    }

    @Test
    void legacyEmailStoredOnlyAsContactCanLogIn() {
        var farmer = loginAccount("legacy", "legacy@example.invalid", null, "legacy-hash");
        when(farmers.findByContact("legacy@example.invalid")).thenReturn(java.util.Optional.of(farmer));
        when(encoder.matches("Correct123", "legacy-hash")).thenReturn(true);

        assertThat(service.login(" legacy@example.invalid ", "Correct123")).isSameAs(farmer);
        verify(farmers).findByEmailIgnoreCase("legacy@example.invalid");
        verify(farmers).findByContact("legacy@example.invalid");
    }

    @Test
    void secondaryEmailStillLogsInWithoutContactFallback() {
        var farmer = loginAccount("secondary", "0712345678", "email@example.invalid", "email-hash");
        when(farmers.findByEmailIgnoreCase("email@example.invalid")).thenReturn(java.util.Optional.of(farmer));
        when(encoder.matches("Correct123", "email-hash")).thenReturn(true);

        assertThat(service.login("email@example.invalid", "Correct123")).isSameAs(farmer);
        verify(farmers, never()).findByContact(anyString());
    }

    @Test
    void legacyEmailContactStillRejectsWrongPassword() {
        var farmer = loginAccount("legacy", "legacy@example.invalid", null, "legacy-hash");
        when(farmers.findByContact("legacy@example.invalid")).thenReturn(java.util.Optional.of(farmer));

        assertThatThrownBy(() -> service.login("legacy@example.invalid", "Wrong123"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void matchingEmailAccountCannotFallBackToAnotherAccountsContactOnWrongPassword() {
        var primary = loginAccount("primary", "0712345678", "shared@example.invalid", "primary-hash");
        var legacy = loginAccount("legacy", "shared@example.invalid", null, "legacy-hash");
        when(farmers.findByEmailIgnoreCase("shared@example.invalid")).thenReturn(java.util.Optional.of(primary));
        when(farmers.findByContact("shared@example.invalid")).thenReturn(java.util.Optional.of(legacy));
        when(encoder.matches("Legacy123", "legacy-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login("shared@example.invalid", "Legacy123"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(farmers, never()).findByContact(anyString());
        verify(encoder, never()).matches("Legacy123", "legacy-hash");
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
