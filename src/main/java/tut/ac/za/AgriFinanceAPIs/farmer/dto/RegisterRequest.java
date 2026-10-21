package tut.ac.za.AgriFinanceAPIs.farmer.dto;

public class RegisterRequest {
    private String name;
    private String location;
    private String contact;
    private String password;
    private String email;
    /** FARMER, BUYER or SUPPLIER. ADMIN cannot be chosen at sign-up. */
    private String role;
    private String language;
    /** The person accepted the terms of use and privacy notice (POPIA). Recorded as consent. */
    private Boolean acceptTerms;

    public RegisterRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Boolean getAcceptTerms() { return acceptTerms; }
    public void setAcceptTerms(Boolean acceptTerms) { this.acceptTerms = acceptTerms; }
}
