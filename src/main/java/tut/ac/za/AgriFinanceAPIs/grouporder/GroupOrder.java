package tut.ac.za.AgriFinanceAPIs.grouporder;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.UUID;

@Entity @Table(name="group_orders")
public class GroupOrder {
 @Id @Column(length=36) private String id;
 @Column(name="product_id",nullable=false,length=36) private String productId;
 @Column(nullable=false,length=50) private String status="open";
 @Column(name="target_quantity",nullable=false) private Integer targetQuantity;
 @Column(name="current_quantity",nullable=false) private Integer currentQuantity=0;
 @Column(name="discount_rate",precision=5,scale=2) private BigDecimal discountRate=BigDecimal.ZERO;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist public void prePersist(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=LocalDateTime.now();if(status==null)status="open";if(currentQuantity==null)currentQuantity=0;if(discountRate==null)discountRate=BigDecimal.ZERO;}
 public String getId(){return id;} public void setId(String v){id=v;}
 public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
 public String getStatus(){return status;} public void setStatus(String v){status=v;}
 public Integer getTargetQuantity(){return targetQuantity;} public void setTargetQuantity(Integer v){targetQuantity=v;}
 public Integer getCurrentQuantity(){return currentQuantity;} public void setCurrentQuantity(Integer v){currentQuantity=v;}
 public BigDecimal getDiscountRate(){return discountRate;} public void setDiscountRate(BigDecimal v){discountRate=v;}
 public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
