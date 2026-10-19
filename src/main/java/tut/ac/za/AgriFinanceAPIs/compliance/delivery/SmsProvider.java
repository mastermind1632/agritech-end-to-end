package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

public interface SmsProvider {
    /**
     * Send an SMS to the given phone number.
     * Returns true if the provider accepted the message, false otherwise.
     * Throws are expected to be handled by the caller; returning false is the
     * "delivery declined, try again later" signal.
     */
    boolean send(String toPhoneNumber, String message);
}
