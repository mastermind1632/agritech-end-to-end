package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

@Entity
@Table(name = "export_products")
public class ExportProduct {
    @Id
    @Column(length = 40)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "hs_code", length = 20)
    private String hsCode;
    @Column(length = 40)
    private String category;
    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHsCode() { return hsCode; }
    public void setHsCode(String hsCode) { this.hsCode = hsCode; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
