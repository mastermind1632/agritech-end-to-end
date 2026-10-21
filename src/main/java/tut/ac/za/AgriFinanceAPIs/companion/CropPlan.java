package tut.ac.za.AgriFinanceAPIs.companion;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One crop in the business plan: area, expected yield and price, cost per hectare and season. */
@Entity
@Table(name = "crop_plans")
public class CropPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(nullable = false, length = 60)
    private String crop;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hectares;

    @Column(name = "yield_per_ha", nullable = false, precision = 12, scale = 2)
    private BigDecimal yieldPerHa;

    @Column(name = "yield_unit", nullable = false, length = 20)
    private String yieldUnit;

    @Column(name = "expected_price", precision = 12, scale = 2)
    private BigDecimal expectedPrice;

    @Column(name = "cost_per_ha", precision = 12, scale = 2)
    private BigDecimal costPerHa;

    @Column(name = "plant_month", nullable = false)
    private int plantMonth;

    @Column(name = "harvest_month", nullable = false)
    private int harvestMonth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }
    public BigDecimal getHectares() { return hectares; }
    public void setHectares(BigDecimal hectares) { this.hectares = hectares; }
    public BigDecimal getYieldPerHa() { return yieldPerHa; }
    public void setYieldPerHa(BigDecimal yieldPerHa) { this.yieldPerHa = yieldPerHa; }
    public String getYieldUnit() { return yieldUnit; }
    public void setYieldUnit(String yieldUnit) { this.yieldUnit = yieldUnit; }
    public BigDecimal getExpectedPrice() { return expectedPrice; }
    public void setExpectedPrice(BigDecimal expectedPrice) { this.expectedPrice = expectedPrice; }
    public BigDecimal getCostPerHa() { return costPerHa; }
    public void setCostPerHa(BigDecimal costPerHa) { this.costPerHa = costPerHa; }
    public int getPlantMonth() { return plantMonth; }
    public void setPlantMonth(int plantMonth) { this.plantMonth = plantMonth; }
    public int getHarvestMonth() { return harvestMonth; }
    public void setHarvestMonth(int harvestMonth) { this.harvestMonth = harvestMonth; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
