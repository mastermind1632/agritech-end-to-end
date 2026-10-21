package tut.ac.za.AgriFinanceAPIs.farmer;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.*;
import tut.ac.za.AgriFinanceAPIs.security.AuthService;
import tut.ac.za.AgriFinanceAPIs.security.Totp;

@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService farmerService;
    private final AuthService authService;
    private final AuditService audit;

    public FarmerController(FarmerService farmerService, AuthService authService, AuditService audit) {
        this.farmerService = farmerService;
        this.authService = authService;
        this.audit = audit;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request, HttpServletRequest http) {
        Farmer farmer = farmerService.register(request);
        farmerService.recordLogin(farmer);
        return authService.issue(farmer, http.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request, HttpServletRequest http) {
        Farmer farmer = farmerService.login(request.getContact(), request.getPassword());
        if (farmer.isMfaEnabled()) {
            if (request.getOtp() == null || request.getOtp().isBlank()) return AuthResponse.mfaChallenge();
            if (!Totp.verify(farmer.getMfaSecret(), request.getOtp(), System.currentTimeMillis())) {
                audit.record(farmer.getId(), "MFA_FAILED", "FARMER", farmer.getId(), null);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The authenticator code is not correct");
            }
        }
        farmerService.recordLogin(farmer);
        return authService.issue(farmer, http.getHeader("User-Agent"));
    }

    @GetMapping("/me")
    public FarmerResponse me(Authentication authentication) {
        return new FarmerResponse(farmerService.getById(authentication.getName()));
    }

    @PutMapping("/me")
    public FarmerResponse updateMe(@RequestBody UpdateProfileRequest request, Authentication authentication) {
        return new FarmerResponse(farmerService.updateProfile(authentication.getName(), request));
    }

    @GetMapping("/{id}")
    public FarmerResponse getFarmer(@PathVariable String id, Authentication authentication) {
        if (!id.equals(authentication.getName()) && !Roles.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own profile");
        }
        return new FarmerResponse(farmerService.getById(id));
    }
}
