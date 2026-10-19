package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_applications")
public class ExportApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(nullable = false, length = 120)
    private String product;

    @Column(length = 120)
    private String variety;

    @Column(name = "destination_country", nullable = false, length = 100)
    private String destinationCountry;

    @Column(name = "production_location", nullable = false, length = 200)
    private String productionLocation;

    @Column(length = 80)
    private String province;

    @Column(name = "estimated_quantity", nullable = false, precision = 14, scale = 3)
    private BigDecimal estimatedQuantity;

    @Column(name = "quantity_unit", nullable = false, length = 40)
    private String quantityUnit;

    @Column(name = "buyer_details", length = 1000)
    private String buyerDetails;

    @Column(name = "business_registration_status", nullable = false, length = 40)
    private String businessRegistrationStatus;

    @Column(name = "tax_registration_status", nullable = false, length = 40)
    private String taxRegistrationStatus;

    @Column(name = "exporter_registration_status", nullable = false, length = 40)
    private String exporterRegistrationStatus;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "shipment_reference", length = 200)
    private String shipmentReference;

    @Column(name = "shipment_outcome", length = 50)
    private String shipmentOutcome;

    @Column(name = "shipment_date")
    private LocalDate shipmentDate;

    @Column(name = "unresolved_issues", length = 2000)
    private String unresolvedIssues;

    @Column(name = "lessons_learned", length = 2000)
    private String lessonsLearned;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ExportApplication() {
        status = "IN_PROGRESS";
        businessRegistrationStatus = "NOT_ASSESSED";
        taxRegistrationStatus = "NOT_ASSESSED";
        exporterRegistrationStatus = "NOT_ASSESSED";
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = "IN_PROGRESS";
        if (businessRegistrationStatus == null) businessRegistrationStatus = "NOT_ASSESSED";
        if (taxRegistrationStatus == null) taxRegistrationStatus = "NOT_ASSESSED";
        if (exporterRegistrationStatus == null) exporterRegistrationStatus = "NOT_ASSESSED";
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getVariety() { return variety; }
    public void setVariety(String variety) { this.variety = variety; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
    public String getProductionLocation() { return productionLocation; }
    public void setProductionLocation(String productionLocation) { this.productionLocation = productionLocation; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public BigDecimal getEstimatedQuantity() { return estimatedQuantity; }
    public void setEstimatedQuantity(BigDecimal estimatedQuantity) { this.estimatedQuantity = estimatedQuantity; }
    public String getQuantityUnit() { return quantityUnit; }
    public void setQuantityUnit(String quantityUnit) { this.quantityUnit = quantityUnit; }
    public String getBuyerDetails() { return buyerDetails; }
    public void setBuyerDetails(String buyerDetails) { this.buyerDetails = buyerDetails; }
    public String getBusinessRegistrationStatus() { return businessRegistrationStatus; }
    public void setBusinessRegistrationStatus(String value) { businessRegistrationStatus = value; }
    public String getTaxRegistrationStatus() { return taxRegistrationStatus; }
    public void setTaxRegistrationStatus(String value) { taxRegistrationStatus = value; }
    public String getExporterRegistrationStatus() { return exporterRegistrationStatus; }
    public void setExporterRegistrationStatus(String value) { exporterRegistrationStatus = value; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getShipmentReference() { return shipmentReference; }
    public void setShipmentReference(String value) { shipmentReference = value; }
    public String getShipmentOutcome() { return shipmentOutcome; }
    public void setShipmentOutcome(String value) { shipmentOutcome = value; }
    public LocalDate getShipmentDate() { return shipmentDate; }
    public void setShipmentDate(LocalDate value) { shipmentDate = value; }
    public String getUnresolvedIssues() { return unresolvedIssues; }
    public void setUnresolvedIssues(String value) { unresolvedIssues = value; }
    public String getLessonsLearned() { return lessonsLearned; }
    public void setLessonsLearned(String value) { lessonsLearned = value; }
    public boolean isClosed() { return closed; }
    public void setClosed(boolean closed) { this.closed = closed; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
