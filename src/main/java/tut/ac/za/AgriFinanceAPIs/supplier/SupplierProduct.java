package tut.ac.za.AgriFinanceAPIs.supplier;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name="supplier_products")
public class SupplierProduct {
    @Id @Column(length=36)
    private String id;
    @Column(name="supplier_id",nullable=false,length=36) private String supplierId;
    @Column(name="product_name",nullable=false,length=150) private String productName;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal price;
    @Column(name="comparison_key",length=150) private String comparisonKey;
    @Column(name="pack_size",precision=12,scale=3) private BigDecimal packSize;
    @Column(name="pack_unit",length=10) private String packUnit;
    public String getComparisonKey(){return comparisonKey;} public void setComparisonKey(String v){comparisonKey=v;}
    public BigDecimal getPackSize(){return packSize;} public void setPackSize(BigDecimal v){packSize=v;}
    public String getPackUnit(){return packUnit;} public void setPackUnit(String v){packUnit=v;}

    @PrePersist public void prePersist(){ if(id==null) id=UUID.randomUUID().toString(); }
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getSupplierId(){return supplierId;} public void setSupplierId(String v){supplierId=v;}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
}
