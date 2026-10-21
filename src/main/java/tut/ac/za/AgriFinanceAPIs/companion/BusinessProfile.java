package tut.ac.za.AgriFinanceAPIs.companion;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** The farm's answers to the companion's intake questionnaire. One per user, reused by every module. */
@Entity
@Table(name = "business_profiles")
public class BusinessProfile {

    @Id
    @Column(name = "farmer_id", length = 36)
    private String farmerId;

    @Column(name = "legal_structure", nullable = false, length = 30)
    private String legalStructure = "NONE";

    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "registration_number", length = 40)
    private String registrationNumber;

    @Column(name = "registered_on")
    private LocalDate registeredOn;

    @Column(name = "tax_number", length = 20)
    private String taxNumber;

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "vat_number", length = 20)
    private String vatNumber;

    @Column(nullable = false)
    private int employees;

    @Column(name = "turnover_band", nullable = false, length = 20)
    private String turnoverBand = "UNDER_50K";

    @Column(length = 300)
    private String crops;

    @Column(precision = 10, scale = 2)
    private BigDecimal hectares;

    @Column(nullable = false)
    private boolean livestock;

    @Column(name = "water_source", length = 30)
    private String waterSource;

    @Column(nullable = false)
    private boolean irrigates;

    @Column(name = "uses_agrochemicals", nullable = false)
    private boolean usesAgrochemicals;

    @Column(name = "sells_to_retailers", nullable = false)
    private boolean sellsToRetailers;

    @Column(name = "export_interest", nullable = false)
    private boolean exportInterest;

    @Column(name = "has_business_account", nullable = false)
    private boolean hasBusinessAccount;

    @Column(name = "financial_year_end_month", nullable = false)
    private int financialYearEndMonth = 2;

    @Column(length = 40)
    private String province;

    /** Set by an administrator after checking the registration documents: the "verified business" badge. */
    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verified_by", length = 36)
    private String verifiedBy;

    @Column(name = "intake_completed_at")
    private LocalDateTime intakeCompletedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public CompanionRules.Profile toRulesProfile() {
        return new CompanionRules.Profile(legalStructure, employees, turnoverBand, vatRegistered, livestock, waterSource,
                irrigates, usesAgrochemicals, sellsToRetailers, exportInterest, hasBusinessAccount, registeredOn,
                financialYearEndMonth, registrationNumber, taxNumber);
    }

    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getLegalStructure() { return legalStructure; }
    public void setLegalStructure(String legalStructure) { this.legalStructure = legalStructure; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public LocalDate getRegisteredOn() { return registeredOn; }
    public void setRegisteredOn(LocalDate registeredOn) { this.registeredOn = registeredOn; }
    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }
    public boolean isVatRegistered() { return vatRegistered; }
    public void setVatRegistered(boolean vatRegistered) { this.vatRegistered = vatRegistered; }
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public int getEmployees() { return employees; }
    public void setEmployees(int employees) { this.employees = employees; }
    public String getTurnoverBand() { return turnoverBand; }
    public void setTurnoverBand(String turnoverBand) { this.turnoverBand = turnoverBand; }
    public String getCrops() { return crops; }
    public void setCrops(String crops) { this.crops = crops; }
    public BigDecimal getHectares() { return hectares; }
    public void setHectares(BigDecimal hectares) { this.hectares = hectares; }
    public boolean isLivestock() { return livestock; }
    public void setLivestock(boolean livestock) { this.livestock = livestock; }
    public String getWaterSource() { return waterSource; }
    public void setWaterSource(String waterSource) { this.waterSource = waterSource; }
    public boolean isIrrigates() { return irrigates; }
    public void setIrrigates(boolean irrigates) { this.irrigates = irrigates; }
    public boolean isUsesAgrochemicals() { return usesAgrochemicals; }
    public void setUsesAgrochemicals(boolean usesAgrochemicals) { this.usesAgrochemicals = usesAgrochemicals; }
    public boolean isSellsToRetailers() { return sellsToRetailers; }
    public void setSellsToRetailers(boolean sellsToRetailers) { this.sellsToRetailers = sellsToRetailers; }
    public boolean isExportInterest() { return exportInterest; }
    public void setExportInterest(boolean exportInterest) { this.exportInterest = exportInterest; }
    public boolean isHasBusinessAccount() { return hasBusinessAccount; }
    public void setHasBusinessAccount(boolean hasBusinessAccount) { this.hasBusinessAccount = hasBusinessAccount; }
    public int getFinancialYearEndMonth() { return financialYearEndMonth; }
    public void setFinancialYearEndMonth(int financialYearEndMonth) { this.financialYearEndMonth = financialYearEndMonth; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getIntakeCompletedAt() { return intakeCompletedAt; }
    public void setIntakeCompletedAt(LocalDateTime intakeCompletedAt) { this.intakeCompletedAt = intakeCompletedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
