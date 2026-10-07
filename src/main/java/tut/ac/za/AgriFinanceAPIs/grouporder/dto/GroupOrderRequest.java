package tut.ac.za.AgriFinanceAPIs.grouporder.dto;
import java.math.BigDecimal;
public class GroupOrderRequest {
 private String productId; private Integer targetQuantity; private BigDecimal discountRate;
 public GroupOrderRequest(){}
 public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
 public Integer getTargetQuantity(){return targetQuantity;} public void setTargetQuantity(Integer v){targetQuantity=v;}
 public BigDecimal getDiscountRate(){return discountRate;} public void setDiscountRate(BigDecimal v){discountRate=v;}
}
