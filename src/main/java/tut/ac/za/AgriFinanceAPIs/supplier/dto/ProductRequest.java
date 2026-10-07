package tut.ac.za.AgriFinanceAPIs.supplier.dto;
import java.math.BigDecimal;
public class ProductRequest {
    private String productName;
    private BigDecimal price;
    public ProductRequest(){}
    public String getProductName(){return productName;} public void setProductName(String v){productName=v;}
    public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;}
}
