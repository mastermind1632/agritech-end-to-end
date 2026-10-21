package tut.ac.za.AgriFinanceAPIs.statements;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A farmer's correction ("ENGEN TZANEEN" is FUEL), reused for every future statement. */
@Entity
@Table(name = "category_memory")
public class CategoryMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "merchant_key", nullable = false, length = 100)
    private String merchantKey;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private int uses = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getMerchantKey() { return merchantKey; }
    public void setMerchantKey(String merchantKey) { this.merchantKey = merchantKey; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getUses() { return uses; }
    public void setUses(int uses) { this.uses = uses; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
