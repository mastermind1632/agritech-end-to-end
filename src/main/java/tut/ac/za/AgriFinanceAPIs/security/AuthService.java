package tut.ac.za.AgriFinanceAPIs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.Hashing;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.AuthResponse;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.FarmerResponse;
import tut.ac.za.AgriFinanceAPIs.notification.ChannelSender;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sessions and account security: short-lived access tokens with rotating refresh tokens, logout, password reset
 * by email or SMS, password change and authenticator-app MFA.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtService jwt;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final FarmerRepository farmers;
    private final PasswordEncoder encoder;
    private final ChannelSender channels;
    private final AuditService audit;
    private final RateLimiter limiter;
    private final long refreshDays;
    private final String publicUrl;
    private final boolean devShowResetLink;

    public AuthService(JwtService jwt, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                       FarmerRepository farmers, PasswordEncoder encoder, ChannelSender channels, AuditService audit,
                       RateLimiter limiter,
                       @Value("${app.security.refresh-token-days:14}") long refreshDays,
                       @Value("${app.public-url:http://localhost:5173}") String publicUrl,
                       @Value("${app.auth.dev-show-reset-link:false}") boolean devShowResetLink) {
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.farmers = farmers;
        this.encoder = encoder;
        this.channels = channels;
        this.audit = audit;
        this.limiter = limiter;
        this.refreshDays = refreshDays;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        this.devShowResetLink = devShowResetLink;
    }

    /** Password rule for new passwords: at least 8 characters with a letter and a number. */
    public static void checkPasswordStrength(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters and include a letter and a number");
        }
        if (password.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
    }

    @Transactional
    public AuthResponse issue(Farmer f, String userAgent) {
        String raw = Hashing.randomToken(48);
        RefreshToken rt = new RefreshToken();
        rt.setFarmerId(f.getId());
        rt.setTokenHash(Hashing.sha256(raw));
        rt.setExpiresAt(LocalDateTime.now().plusDays(refreshDays));
        rt.setUserAgent(userAgent != null && userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent);
        refreshTokens.save(rt);
        return new AuthResponse(jwt.createToken(f.getId(), f.getContact(), f.getRole()), new FarmerResponse(f), raw,
                jwt.expirationSeconds(), false);
    }

    /**
     * Swaps a refresh token for a new pair. A token that was already used is a sign it was stolen, so every session
     * of that user is ended (refresh-token reuse detection).
     */
    @Transactional
    public AuthResponse refresh(String raw, String userAgent) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        RefreshToken rt = refreshTokens.findForUpdate(Hashing.sha256(raw)).orElseThrow(AuthService::unauthorized);
        LocalDateTime now = LocalDateTime.now();
        if (rt.getRevokedAt() != null) {
            refreshTokens.revokeAll(rt.getFarmerId(), now);
            audit.record(rt.getFarmerId(), "REFRESH_TOKEN_REUSED", "SESSION", rt.getId(), "All sessions revoked");
            throw unauthorized();
        }
        if (!rt.isUsable(now)) throw unauthorized();
        Farmer f = farmers.findById(rt.getFarmerId()).orElseThrow(AuthService::unauthorized);
        if (!f.isActive()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended");
        AuthResponse next = issue(f, userAgent);
        rt.setRevokedAt(now);
        refreshTokens.findForUpdate(Hashing.sha256(next.refreshToken())).ifPresent(n -> rt.setReplacedBy(n.getId()));
        refreshTokens.save(rt);
        return next;
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        refreshTokens.findForUpdate(Hashing.sha256(raw)).ifPresent(rt -> {
            rt.setRevokedAt(LocalDateTime.now());
            refreshTokens.save(rt);
        });
    }

    @Transactional
    public int logoutAll(String farmerId) {
        int n = refreshTokens.revokeAll(farmerId, LocalDateTime.now());
        audit.record(farmerId, "LOGOUT_ALL", "SESSION", farmerId, n + " sessions");
        return n;
    }

    /** Always answers the same way, so it cannot be used to discover which numbers or emails are registered. */
    @Transactional
    public Map<String, Object> forgotPassword(String identifier) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "If an account matches, a reset link has been sent by email or SMS. It is valid for 30 minutes.");
        if (identifier == null || identifier.isBlank()) return out;
        String key = "reset:" + identifier.trim().toLowerCase() + ":" + AuditService.clientIp();
        if (!limiter.tryAcquire(key, 3, 60 * 60 * 1000L)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many reset requests. Please try again later.");
        }
        String id = identifier.trim();
        Optional<Farmer> found = id.contains("@")
                ? farmers.findByEmailIgnoreCase(id).or(() -> farmers.findByContact(id))
                : farmers.findByContact(id);
        if (found.isEmpty() || !found.get().isActive()) return out;
        Farmer f = found.get();
        String raw = Hashing.randomToken(32);
        PasswordResetToken t = new PasswordResetToken();
        t.setFarmerId(f.getId());
        t.setTokenHash(Hashing.sha256(raw));
        t.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        resetTokens.save(t);
        String link = publicUrl + "/?reset=" + raw;
        String email = f.getEmail();
        if ((email == null || email.isBlank()) && f.getContact() != null && f.getContact().contains("@")) {
            email = f.getContact();
        }
        channels.sendSecurityMessage(email, f.getContact(), "Reset your AgriTech password",
                "Someone asked to reset the password for your AgriTech account. Open this link within 30 minutes to choose a new password:\n"
                        + link + "\nIf this was not you, ignore this message.");
        audit.record(f.getId(), "PASSWORD_RESET_REQUESTED", "FARMER", f.getId(), null);
        if (devShowResetLink) {
            log.warn("DEV ONLY reset link for {}: {}", f.getContact(), link);
            out.put("devResetLink", link);
        }
        return out;
    }

    @Transactional
    public void resetPassword(String raw, String newPassword) {
        checkPasswordStrength(newPassword);
        PasswordResetToken t = resetTokens.findByTokenHash(raw == null ? "" : Hashing.sha256(raw))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is not valid"));
        if (t.getUsedAt() != null || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has expired. Please ask for a new one.");
        }
        Farmer f = farmers.findById(t.getFarmerId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account not found"));
        f.setPasswordHash(encoder.encode(newPassword));
        farmers.save(f);
        t.setUsedAt(LocalDateTime.now());
        resetTokens.save(t);
        refreshTokens.revokeAll(f.getId(), LocalDateTime.now());
        audit.record(f.getId(), "PASSWORD_RESET", "FARMER", f.getId(), "All sessions revoked");
    }

    @Transactional
    public void changePassword(String farmerId, String current, String next) {
        Farmer f = farmers.findById(farmerId).orElseThrow(AuthService::unauthorized);
        if (current == null || !encoder.matches(current, f.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is not correct");
        }
        checkPasswordStrength(next);
        f.setPasswordHash(encoder.encode(next));
        farmers.save(f);
        audit.record(farmerId, "PASSWORD_CHANGED", "FARMER", farmerId, null);
    }

    @Transactional
    public Map<String, String> mfaSetup(String farmerId) {
        Farmer f = farmers.findById(farmerId).orElseThrow(AuthService::unauthorized);
        if (f.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Two-step sign-in is already on");
        String secret = Totp.newSecret();
        f.setMfaSecret(secret);
        farmers.save(f);
        String account = f.getEmail() != null ? f.getEmail() : f.getContact();
        return Map.of("secret", secret, "otpauthUri", Totp.otpauthUri("AgriTech", account, secret));
    }

    @Transactional
    public void mfaEnable(String farmerId, String code) {
        Farmer f = farmers.findById(farmerId).orElseThrow(AuthService::unauthorized);
        if (f.getMfaSecret() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start the set-up first");
        if (!Totp.verify(f.getMfaSecret(), code, System.currentTimeMillis())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is not correct. Check the time on your phone and try again.");
        }
        f.setMfaEnabled(true);
        farmers.save(f);
        audit.record(farmerId, "MFA_ENABLED", "FARMER", farmerId, null);
    }

    @Transactional
    public void mfaDisable(String farmerId, String code) {
        Farmer f = farmers.findById(farmerId).orElseThrow(AuthService::unauthorized);
        if (!f.isMfaEnabled()) return;
        if (!Totp.verify(f.getMfaSecret(), code, System.currentTimeMillis())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is not correct");
        }
        f.setMfaEnabled(false);
        f.setMfaSecret(null);
        farmers.save(f);
        audit.record(farmerId, "MFA_DISABLED", "FARMER", farmerId, null);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredSessions() {
        int n = refreshTokens.deleteExpired(LocalDateTime.now().minusDays(1));
        if (n > 0) log.info("Removed {} expired refresh tokens", n);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again.");
    }
}
