package tut.ac.za.AgriFinanceAPIs.compliance;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_compliance")
public class BusinessCompliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id")
    private String farmerId;

    @Column(name = "company_registered")
    private boolean companyRegistered = false;

    @Column(name = "cipc_registration_number")
    private String cipcRegistrationNumber;

    @Column(name = "tax_registered")
    private boolean taxRegistered = false;

    @Column(name = "vat_registered")
    private boolean vatRegistered = false;

    @Column(name = "has_employees")
    private boolean hasEmployees = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public boolean isCompanyRegistered() { return companyRegistered; }
    public void setCompanyRegistered(boolean companyRegistered) { this.companyRegistered = companyRegistered; }
    public String getCipcRegistrationNumber() { return cipcRegistrationNumber; }
    public void setCipcRegistrationNumber(String cipcRegistrationNumber) { this.cipcRegistrationNumber = cipcRegistrationNumber; }
    public boolean isTaxRegistered() { return taxRegistered; }
    public void setTaxRegistered(boolean taxRegistered) { this.taxRegistered = taxRegistered; }
    public boolean isVatRegistered() { return vatRegistered; }
    public void setVatRegistered(boolean vatRegistered) { this.vatRegistered = vatRegistered; }
    public boolean isHasEmployees() { return hasEmployees; }
    public void setHasEmployees(boolean hasEmployees) { this.hasEmployees = hasEmployees; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
