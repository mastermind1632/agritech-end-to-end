package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A farmer's produce for sale on the Farm Market (farmer-to-buyer). */
@Entity
@Table(name = "market_listings", indexes = {
        @Index(name = "idx_market_listings_seller", columnList = "seller_id"),
        @Index(name = "idx_market_listings_status", columnList = "status")
})
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, length = 36)
    private String sellerId;

    @Column(nullable = false, length = 100)
    private String product;

    @Column(length = 50)
    private String category;

    @Column(length = 30)
    private String grade;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "price_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(name = "quantity_available", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityAvailable;

    /** The farmer's own cost per unit. Private: only the owner ever sees it. Used to work out profit. */
    @Column(name = "cost_per_unit", precision = 12, scale = 2)
    private BigDecimal costPerUnit;

    /** Optional sale price; applies while it is below the normal price and the end date has not passed. */
    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_ends_on")
    private LocalDate saleEndsOn;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "available_until")
    private LocalDate availableUntil;

    @Column(length = 1000)
    private String description;

    @Column(length = 150)
    private String location;

    private Double latitude;
    private Double longitude;

    /** ACTIVE, PAUSED, SOLD_OUT or REMOVED (removed listings are kept so past orders stay traceable). */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Listing() { }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public BigDecimal getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(BigDecimal quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public BigDecimal getCostPerUnit() { return costPerUnit; }
    public void setCostPerUnit(BigDecimal costPerUnit) { this.costPerUnit = costPerUnit; }
    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }
    public LocalDate getSaleEndsOn() { return saleEndsOn; }
    public void setSaleEndsOn(LocalDate saleEndsOn) { this.saleEndsOn = saleEndsOn; }

    public boolean isOnSale(LocalDate today) {
        return salePrice != null && salePrice.compareTo(pricePerUnit) < 0
                && (saleEndsOn == null || !saleEndsOn.isBefore(today));
    }

    public BigDecimal effectivePrice(LocalDate today) { return isOnSale(today) ? salePrice : pricePerUnit; }

    public LocalDate getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalDate availableFrom) { this.availableFrom = availableFrom; }
    public LocalDate getAvailableUntil() { return availableUntil; }
    public void setAvailableUntil(LocalDate availableUntil) { this.availableUntil = availableUntil; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
