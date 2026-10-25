package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One export attempt (product to destination), moving through the eight stages from eligibility to shipped. */
@Entity
@Table(name = "export_workflows")
public class ExportWorkflow {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    @Column(name = "destination_code", nullable = false, length = 20)
    private String destinationCode;

    @Column(name = "current_stage", nullable = false)
    private int currentStage = 1;

    /** ACTIVE, SHIPPED, CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "target_ship_date")
    private LocalDate targetShipDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal volume;

    @Column(name = "volume_unit", length = 20)
    private String volumeUnit;

    @Column(length = 1000)
    private String outcome;

    @Column(length = 1000)
    private String lessons;

        // --- SOP Specific Fields (Agricultural Product Standards Act) ---
    @Column(name = "fbo_code", length = 50)
    private String fboCode;

    @Column(name = "variety", length = 100)
    private String variety;

    @Column(name = "class_category", length = 100)
    private String classCategory;

    @Column(name = "package_count")
    private Integer packageCount;

    @Column(name = "package_type", length = 50)
    private String packageType; // e.g., cartons, bags, pallets

    @Column(name = "pallet_count")
    private Integer palletCount;

    @Column(name = "container_number", length = 50)
    private String containerNumber;

    @Column(name = "transport_mode", length = 20)
    private String transportMode; // SEA, AIR, LAND

    @Column(name = "inspection_date")
    private LocalDate inspectionDate;

    @Column(name = "certificate_valid_until")
    private LocalDate certificateValidUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDestinationCode() { return destinationCode; }
    public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
    public int getCurrentStage() { return currentStage; }
    public void setCurrentStage(int currentStage) { this.currentStage = currentStage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getTargetShipDate() { return targetShipDate; }
    public void setTargetShipDate(LocalDate targetShipDate) { this.targetShipDate = targetShipDate; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public String getVolumeUnit() { return volumeUnit; }
    public void setVolumeUnit(String volumeUnit) { this.volumeUnit = volumeUnit; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getLessons() { return lessons; }
    public void setLessons(String lessons) { this.lessons = lessons; }
        public String getFboCode() { return fboCode; }
    public void setFboCode(String fboCode) { this.fboCode = fboCode; }
    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }
    public String getClassCategory() { return classCategory; }
    public void setClassCategory(String classCategory) { this.classCategory = classCategory; }
    public Integer getPackageCount() { return packageCount; }
    public void setPackageCount(Integer packageCount) { this.packageCount = packageCount; }
    public String getPackageType() { return packageType; }
    public void setPackageType(String packageType) { this.packageType = packageType; }
    public Integer getPalletCount() { return palletCount; }
    public void setPalletCount(Integer palletCount) { this.palletCount = palletCount; }
    public String getContainerNumber() { return containerNumber; }
    public void setContainerNumber(String containerNumber) { this.containerNumber = containerNumber; }
    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    public LocalDate getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(LocalDate inspectionDate) { this.inspectionDate = inspectionDate; }
    public LocalDate getCertificateValidUntil() { return certificateValidUntil; }
    public void setCertificateValidUntil(LocalDate certificateValidUntil) { this.certificateValidUntil = certificateValidUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}

