package tut.ac.za.AgriFinanceAPIs.supplier.dto;
import java.math.BigDecimal;
public class ProductRequest {
    private String productName;
    private BigDecimal price;
    private String comparisonKey;
    private BigDecimal packSize;
    private String packUnit;
    public String getComparisonKey(){return comparisonKey;} public void setComparisonKey(String v){comparisonKey=v;}
    public BigDecimal getPackSize(){return packSize;} public void setPackSize(BigDecimal v){packSize=v;}
    public String getPackUnit(){return packUnit;} public void setPackUnit(String v){packUnit=v;}
    public ProductRequest(){}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
}
