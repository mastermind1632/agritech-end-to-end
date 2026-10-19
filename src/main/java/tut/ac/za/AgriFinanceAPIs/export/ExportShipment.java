package tut.ac.za.AgriFinanceAPIs.export;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_shipments")
public class ExportShipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "destination_country", nullable = false)
    private String destinationCountry;

    @Column(name = "export_stage")
    private String exportStage = "Eligibility";

    @Column(name = "ppecb_inspection_booked")
    private boolean ppecbInspectionBooked = false;

    @Column(name = "phytosanitary_status")
    private String phytosanitaryStatus = "Pending";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getDestinationCountry() { return destinationCountry; }
    public void setDestinationCountry(String destinationCountry) { this.destinationCountry = destinationCountry; }
    public String getExportStage() { return exportStage; }
    public void setExportStage(String exportStage) { this.exportStage = exportStage; }
    public boolean isPpecbInspectionBooked() { return ppecbInspectionBooked; }
    public void setPpecbInspectionBooked(boolean ppecbInspectionBooked) { this.ppecbInspectionBooked = ppecbInspectionBooked; }
    public String getPhytosanitaryStatus() { return phytosanitaryStatus; }
    public void setPhytosanitaryStatus(String phytosanitaryStatus) { this.phytosanitaryStatus = phytosanitaryStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
