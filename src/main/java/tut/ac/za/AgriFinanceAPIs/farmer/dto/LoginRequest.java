package tut.ac.za.AgriFinanceAPIs.farmer.dto;

public class LoginRequest {
    private String contact;
    private String password;
    /** 6-digit authenticator code, only needed when multi-factor authentication is switched on. */
    private String otp;

    public LoginRequest() {}

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
