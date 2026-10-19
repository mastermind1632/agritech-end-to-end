package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "export_requirements")
public class ExportRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_key", nullable = false, unique = true, length = 100)
    private String catalogKey;

    @Column(nullable = false, length = 120)
    private String product;

    @Column(nullable = false, length = 100)
    private String destination;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 1200)
    private String explanation;

    @Column(name = "hs_code", length = 40)
    private String hsCode;

    @Column(nullable = false, length = 160)
    private String authority;

    @Column(nullable = false, length = 40)
    private String applicability;

    @Column(name = "documents_needed", length = 1000)
    private String documentsNeeded;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "last_verified_date")
    private LocalDate lastVerifiedDate;

    @Column(name = "verification_status", nullable = false, length = 40)
    private String verificationStatus;

    @Column(length = 1500)
    private String followUp;

    @Column(name = "lead_time", length = 100)
    private String leadTime;

    @Column(length = 100)
    private String cost;

    protected ExportRequirement() { }

    public ExportRequirement(String catalogKey, String product, String destination, String name, String explanation,
                             String hsCode, String authority, String applicability, String documentsNeeded,
                             String sourceUrl, String verificationStatus, String followUp) {
        this.catalogKey = catalogKey;
        this.product = product;
        this.destination = destination;
        this.name = name;
        this.explanation = explanation;
        this.hsCode = hsCode;
        this.authority = authority;
        this.applicability = applicability;
        this.documentsNeeded = documentsNeeded;
        this.sourceUrl = sourceUrl;
        this.verificationStatus = verificationStatus;
        this.followUp = followUp;
    }

    public Long getId() { return id; }
    public String getCatalogKey() { return catalogKey; }
    public String getProduct() { return product; }
    public String getDestination() { return destination; }
    public String getName() { return name; }
    public String getExplanation() { return explanation; }
    public String getHsCode() { return hsCode; }
    public String getAuthority() { return authority; }
    public String getApplicability() { return applicability; }
    public String getDocumentsNeeded() { return documentsNeeded; }
    public String getSourceUrl() { return sourceUrl; }
    public LocalDate getLastVerifiedDate() { return lastVerifiedDate; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getFollowUp() { return followUp; }
    public String getLeadTime() { return leadTime; }
    public String getCost() { return cost; }
}
