package tut.ac.za.AgriFinanceAPIs.statements;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** How untagged costs of one category are spread over crops: BY_HECTARES or FIXED_CROP. */
@Entity
@Table(name = "allocation_rules")
public class AllocationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(length = 60)
    private String crop;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
