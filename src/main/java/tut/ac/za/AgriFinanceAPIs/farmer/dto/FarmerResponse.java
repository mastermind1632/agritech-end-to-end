package tut.ac.za.AgriFinanceAPIs.farmer.dto;

import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;

import java.time.LocalDateTime;

public class FarmerResponse {
    private String id;
    private String name;
    private String location;
    private String contact;
    private LocalDateTime createdAt;

    public FarmerResponse(Farmer farmer) {
        this.id = farmer.getId();
        this.name = farmer.getName();
        this.location = farmer.getLocation();
        this.contact = farmer.getContact();
        this.createdAt = farmer.getCreatedAt();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public String getContact() { return contact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
