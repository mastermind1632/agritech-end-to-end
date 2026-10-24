package tut.ac.za.AgriFinanceAPIs.security;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.notification.ChannelSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private FarmerRepository farmers;
    private PasswordResetTokenRepository resetTokens;
    private ChannelSender channels;
    private AuthService service;

    @BeforeEach
    void setUp() {
        farmers = mock(FarmerRepository.class);
        resetTokens = mock(PasswordResetTokenRepository.class);
        channels = mock(ChannelSender.class);
        service = new AuthService(mock(JwtService.class), mock(RefreshTokenRepository.class), resetTokens,
                farmers, mock(PasswordEncoder.class), channels, mock(AuditService.class),
                new RateLimiter(), 14, "http://agritech.test", false);
    }

    private Farmer account(String id, String contact, String email) {
        var farmer = new Farmer();
        farmer.setId(id);
        farmer.setContact(contact);
        farmer.setEmail(email);
        return farmer;
    }

    @Test
    void passwordResetFindsLegacyEmailContactWithoutDisclosingAccountExistence() {
        var legacy = account("legacy", "legacy@example.invalid", null);
        when(farmers.findByContact("legacy@example.invalid")).thenReturn(Optional.of(legacy));

        var result = service.forgotPassword(" legacy@example.invalid ");
        assertThat(result).isEqualTo(service.forgotPassword("missing@example.invalid"));
        assertThat(result).containsOnlyKeys("message");
        var token = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(token.capture());
        assertThat(token.getValue().getFarmerId()).isEqualTo("legacy");
        assertThat(token.getValue().getTokenHash()).hasSize(64);
        assertThat(token.getValue().getExpiresAt()).isNotNull();
        verify(farmers).findByEmailIgnoreCase("legacy@example.invalid");
        verify(farmers).findByContact("legacy@example.invalid");
        verify(channels).sendSecurityMessage(eq("legacy@example.invalid"), eq("legacy@example.invalid"),
                eq("Reset your AgriTech password"), anyString());
    }

    @Test
    void passwordResetPrioritizesSecondaryEmailOverAnotherAccountsLegacyContact() {
        var primary = account("primary", "0712345678", "shared@example.invalid");
        var legacy = account("legacy", "shared@example.invalid", null);
        when(farmers.findByEmailIgnoreCase("shared@example.invalid")).thenReturn(Optional.of(primary));
        when(farmers.findByContact("shared@example.invalid")).thenReturn(Optional.of(legacy));

        service.forgotPassword("shared@example.invalid");

        var token = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(token.capture());
        assertThat(token.getValue().getFarmerId()).isEqualTo("primary");
        verify(farmers, never()).findByContact(anyString());
        verify(channels).sendSecurityMessage(eq("shared@example.invalid"), eq("0712345678"),
                eq("Reset your AgriTech password"), anyString());
    }

    @Test
    void separateEmailRecipientTakesPriorityOverEmailShapedContact() {
        var farmer = account("primary", "contact@example.invalid", "receipts@example.invalid");
        when(farmers.findByContact("contact@example.invalid")).thenReturn(Optional.of(farmer));

        service.forgotPassword("contact@example.invalid");

        verify(channels).sendSecurityMessage(eq("receipts@example.invalid"), eq("contact@example.invalid"),
                eq("Reset your AgriTech password"), anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void phoneContactWithoutSeparateEmailKeepsSmsRecipient(String email) {
        var farmer = account("phone", "0712345678", email);
        when(farmers.findByContact("0712345678")).thenReturn(Optional.of(farmer));

        service.forgotPassword("0712345678");

        verify(channels).sendSecurityMessage(eq(email), eq("0712345678"),
                eq("Reset your AgriTech password"), anyString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void emailContactSuppliesRecipientWhenSeparateEmailIsMissingOrBlank(String email) {
        var farmer = account("legacy", "legacy@example.invalid", email);
        when(farmers.findByContact("legacy@example.invalid")).thenReturn(Optional.of(farmer));

        service.forgotPassword("legacy@example.invalid");

        verify(channels).sendSecurityMessage(eq("legacy@example.invalid"), eq("legacy@example.invalid"),
                eq("Reset your AgriTech password"), anyString());
    }

    @Test
    void passwordResetDoesNotBypassSuspendedEmailAccountUsingLegacyContact() {
        var primary = account("primary", "0712345678", "shared@example.invalid");
        primary.setStatus("SUSPENDED");
        when(farmers.findByEmailIgnoreCase("shared@example.invalid")).thenReturn(Optional.of(primary));

        assertThat(service.forgotPassword("shared@example.invalid"))
                .isEqualTo(service.forgotPassword("missing@example.invalid"));
        verify(resetTokens, never()).save(any());
        verify(farmers, never()).findByContact("shared@example.invalid");
    }

    @Test
    void legacyContactFallbackPreservesPasswordResetRateLimit() {
        var legacy = account("legacy", "legacy@example.invalid", null);
        when(farmers.findByContact("legacy@example.invalid")).thenReturn(Optional.of(legacy));
        for (int attempt = 0; attempt < 3; attempt++) service.forgotPassword("legacy@example.invalid");

        assertThatThrownBy(() -> service.forgotPassword("legacy@example.invalid"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(farmers, times(3)).findByContact("legacy@example.invalid");
        verify(resetTokens, times(3)).save(any());
    }
}
