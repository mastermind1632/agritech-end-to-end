package tut.ac.za.AgriFinanceAPIs.farmer.dto;

/**
 * token: short-lived access token (Bearer). refreshToken: long-lived, single-use, rotated on every refresh.
 * mfaRequired: the password was right but an authenticator code is still needed (admins with MFA on).
 */
public record AuthResponse(String token, FarmerResponse farmer, String refreshToken, long expiresIn, boolean mfaRequired) {

    public AuthResponse(String token, FarmerResponse farmer) {
        this(token, farmer, null, 0, false);
    }

    public static AuthResponse mfaChallenge() {
        return new AuthResponse(null, null, null, 0, true);
    }
}
