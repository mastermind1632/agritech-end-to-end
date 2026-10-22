package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.RateLimiter;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.RegisterRequest;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.UpdateProfileRequest;
import tut.ac.za.AgriFinanceAPIs.privacy.PrivacyService;
import tut.ac.za.AgriFinanceAPIs.security.AuthService;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class FarmerService {

    public static final Set<String> LANGUAGES = Set.of("en", "af", "nso", "ts", "zu");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private final FarmerRepository farmerRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter limiter;
    private final AuditService audit;
    private final PrivacyService privacy;

    public FarmerService(FarmerRepository farmerRepository, PasswordEncoder passwordEncoder, RateLimiter limiter,
                         AuditService audit, PrivacyService privacy) {
        this.farmerRepository = farmerRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.audit = audit;
        this.privacy = privacy;
    }

    /** Kept for existing callers and tests. */
    public Farmer register(String name, String location, String contact, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setName(name);
        r.setLocation(location);
        r.setContact(contact);
        r.setPassword(password);
        r.setAcceptTerms(true);
        return register(r);
    }

    @Transactional
    public Farmer register(RegisterRequest r) {
        if (r.getName() == null || r.getName().isBlank()) throw bad("Name is required");
        if (r.getName().trim().length() > 150) throw bad("Name is too long");
        if (r.getContact() == null || r.getContact().isBlank()) throw bad("Contact is required");
        AuthService.checkPasswordStrength(r.getPassword());
        if (!Boolean.TRUE.equals(r.getAcceptTerms())) throw bad("Please accept the terms of use and privacy notice");
        String contact = r.getContact().trim();
        if (farmerRepository.findByContact(contact).isPresent()) throw bad("Contact already registered");
        String email = normaliseEmail(r.getEmail());
        if (email != null && farmerRepository.findByEmailIgnoreCase(email).isPresent()) throw bad("Email already registered");
        String role = r.getRole() == null || r.getRole().isBlank() ? Roles.FARMER : r.getRole().trim().toUpperCase();
        if (!Roles.SELF_SERVICE.contains(role)) throw bad("Choose a farmer, buyer or supplier account");

        Farmer farmer = new Farmer();
        farmer.setName(r.getName().trim());
        farmer.setLocation(r.getLocation() == null ? null : r.getLocation().trim());
        farmer.setContact(contact);
        farmer.setEmail(email);
        farmer.setRole(role);
        farmer.setPreferredLanguage(r.getLanguage() != null && LANGUAGES.contains(r.getLanguage()) ? r.getLanguage() : "en");
        farmer.setPasswordHash(passwordEncoder.encode(r.getPassword()));
        Farmer saved = farmerRepository.save(farmer);
        privacy.record(saved.getId(), "TERMS", true);
        privacy.record(saved.getId(), "PRIVACY", true);
        audit.record(saved.getId(), "REGISTERED", "FARMER", saved.getId(), role);
        return saved;
    }

    /**
     * Checks the password with brute-force protection: 5 attempts per contact and address every 15 minutes.
     * The MFA code (when switched on) is checked by the controller after this succeeds.
     */
    @Transactional
    public Farmer login(String contact, String password) {
        if (contact == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact and password are required");
        }
        String key = "login:" + contact.trim().toLowerCase() + ":" + AuditService.clientIp();
        if (!limiter.tryAcquire(key, MAX_ATTEMPTS, WINDOW_MS)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Please wait "
                    + Math.max(1, limiter.retryAfterSeconds(key, WINDOW_MS) / 60) + " minute(s) and try again, or reset your password.");
        }
        String id = contact.trim();
        Farmer farmer = (id.contains("@") ? farmerRepository.findByEmailIgnoreCase(id) : farmerRepository.findByContact(id))
                .orElseThrow(() -> failed(contact));
        if (farmer.getPasswordHash() == null || !passwordEncoder.matches(password, farmer.getPasswordHash())) {
            throw failed(contact);
        }
        if (!farmer.isActive()) {
            audit.record(farmer.getId(), "LOGIN_BLOCKED_SUSPENDED", "FARMER", farmer.getId(), null);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is suspended. Please contact support.");
        }
        limiter.reset(key);
        return farmer;
    }

    @Transactional
    public void recordLogin(Farmer farmer) {
        farmer.setLastLoginAt(LocalDateTime.now());
        farmerRepository.save(farmer);
        audit.record(farmer.getId(), "LOGIN", "FARMER", farmer.getId(), null);
    }

    public Farmer getById(String id) {
        return farmerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found"));
    }

    @Transactional
    public Farmer updateProfile(String id, UpdateProfileRequest r) {
        Farmer f = getById(id);
        if (r.name() != null) {
            if (r.name().isBlank() || r.name().trim().length() > 150) throw bad("Enter a name of up to 150 characters");
            f.setName(r.name().trim());
        }
        if (r.location() != null) f.setLocation(r.location().isBlank() ? null : r.location().trim());
        if (r.email() != null) {
            String email = normaliseEmail(r.email());
            if (email != null) {
                farmerRepository.findByEmailIgnoreCase(email).filter(o -> !o.getId().equals(id))
                        .ifPresent(o -> { throw bad("Email already registered"); });
            }
            f.setEmail(email);
        }
        if (r.preferredLanguage() != null && LANGUAGES.contains(r.preferredLanguage())) f.setPreferredLanguage(r.preferredLanguage());
        return farmerRepository.save(f);
    }

    private ResponseStatusException failed(String contact) {
        audit.record(null, "LOGIN_FAILED", "FARMER", null, "contact=" + mask(contact));
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid contact or password");
    }

    static String normaliseEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String e = email.trim().toLowerCase();
        if (e.length() > 150 || !EMAIL.matcher(e).matches()) throw bad("Enter a valid email address");
        return e;
    }

    private static String mask(String contact) {
        if (contact == null) return "";
        String c = contact.trim();
        return c.length() <= 4 ? "****" : "****" + c.substring(c.length() - 4);
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
