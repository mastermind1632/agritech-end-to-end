package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

@Entity
@Table(name = "export_destinations")
public class ExportDestination {
    @Id
    @Column(length = 20)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 60)
    private String region;
    @Column(nullable = false)
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
