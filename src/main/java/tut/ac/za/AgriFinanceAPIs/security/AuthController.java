package tut.ac.za.AgriFinanceAPIs.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.AuthResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    public record RefreshRequest(String refreshToken) { }
    public record ForgotRequest(String identifier) { }
    public record ResetRequest(String token, String password) { }
    public record ChangePasswordRequest(String currentPassword, String newPassword) { }
    public record CodeRequest(String code) { }

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody RefreshRequest body, HttpServletRequest request) {
        return auth.refresh(body == null ? null : body.refreshToken(), request.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshRequest body) {
        auth.logout(body == null ? null : body.refreshToken());
    }

    @PostMapping("/logout-all")
    public Map<String, Integer> logoutAll(Authentication authentication) {
        return Map.of("sessionsEnded", auth.logoutAll(authentication.getName()));
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgot(@RequestBody ForgotRequest body) {
        return auth.forgotPassword(body == null ? null : body.identifier());
    }

    @PostMapping("/reset-password")
    public Map<String, String> reset(@RequestBody ResetRequest body) {
        auth.resetPassword(body.token(), body.password());
        return Map.of("message", "Your password has been changed. Please sign in.");
    }

    @PostMapping("/change-password")
    public Map<String, String> change(@RequestBody ChangePasswordRequest body, Authentication authentication) {
        auth.changePassword(authentication.getName(), body.currentPassword(), body.newPassword());
        return Map.of("message", "Password changed");
    }

    @PostMapping("/mfa/setup")
    public Map<String, String> mfaSetup(Authentication authentication) {
        return auth.mfaSetup(authentication.getName());
    }

    @PostMapping("/mfa/enable")
    public Map<String, Boolean> mfaEnable(@RequestBody CodeRequest body, Authentication authentication) {
        auth.mfaEnable(authentication.getName(), body.code());
        return Map.of("mfaEnabled", true);
    }

    @PostMapping("/mfa/disable")
    public Map<String, Boolean> mfaDisable(@RequestBody CodeRequest body, Authentication authentication) {
        auth.mfaDisable(authentication.getName(), body.code());
        return Map.of("mfaEnabled", false);
    }
}
