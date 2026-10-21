package tut.ac.za.AgriFinanceAPIs.farmer.dto;

import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;

import java.time.LocalDateTime;

public class FarmerResponse {
    private final String id;
    private final String name;
    private final String location;
    private final String contact;
    private final String email;
    private final String role;
    private final String status;
    private final String preferredLanguage;
    private final boolean mfaEnabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLoginAt;

    public FarmerResponse(Farmer farmer) {
        this.id = farmer.getId();
        this.name = farmer.getName();
        this.location = farmer.getLocation();
        this.contact = farmer.getContact();
        this.email = farmer.getEmail();
        this.role = farmer.getRole();
        this.status = farmer.getStatus();
        this.preferredLanguage = farmer.getPreferredLanguage();
        this.mfaEnabled = farmer.isMfaEnabled();
        this.createdAt = farmer.getCreatedAt();
        this.lastLoginAt = farmer.getLastLoginAt();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getContact() { return contact; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getPreferredLanguage() { return preferredLanguage; }
    public boolean isMfaEnabled() { return mfaEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
