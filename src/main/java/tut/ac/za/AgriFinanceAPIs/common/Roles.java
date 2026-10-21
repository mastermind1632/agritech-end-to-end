package tut.ac.za.AgriFinanceAPIs.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

/**
 * The four roles from the design guide. Permissions are always enforced on the server; the frontend only hides
 * buttons. FARMER sells and buys, BUYER only buys, SUPPLIER runs a supplier catalogue, ADMIN moderates.
 */
public final class Roles {

    public static final String FARMER = "FARMER";
    public static final String BUYER = "BUYER";
    public static final String SUPPLIER = "SUPPLIER";
    public static final String ADMIN = "ADMIN";

    public static final List<String> ALL = List.of(FARMER, BUYER, SUPPLIER, ADMIN);
    /** Roles a person may choose when they sign up. ADMIN is only granted by another admin or the bootstrap. */
    public static final List<String> SELF_SERVICE = List.of(FARMER, BUYER, SUPPLIER);

    private Roles() { }

    public static String normalise(String role) {
        if (role == null || role.isBlank()) return FARMER;
        String r = role.trim().toUpperCase();
        return ALL.contains(r) ? r : FARMER;
    }

    public static boolean has(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    public static boolean isAdmin(Authentication auth) {
        return has(auth, ADMIN);
    }

    public static String of(Authentication auth) {
        if (auth == null) return null;
        return auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).findFirst().orElse(FARMER);
    }

    /** Throws 403 unless the user has one of the roles (admins always pass). */
    public static void require(Authentication auth, String... roles) {
        if (isAdmin(auth)) return;
        if (Arrays.stream(roles).anyMatch(r -> has(auth, r))) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Your account type cannot do this. It needs a " + String.join(" or ", roles).toLowerCase() + " account.");
    }
}
