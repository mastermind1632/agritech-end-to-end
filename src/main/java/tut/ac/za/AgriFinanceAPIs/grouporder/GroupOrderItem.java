package tut.ac.za.AgriFinanceAPIs.grouporder;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="group_order_items")
public class GroupOrderItem {
 @Id @Column(length=36) private String id;
 @Column(name="group_order_id",nullable=false,length=36) private String groupOrderId;
 @Column(name="farmer_id",nullable=false,length=36) private String farmerId;
 @Column(nullable=false) private Integer quantity;
 @Column(name="total_price",nullable=false,precision=12,scale=2) private BigDecimal totalPrice;
 @Column(name="joined_at",nullable=false) private LocalDateTime joinedAt;
 @PrePersist public void prePersist(){if(id==null)id=UUID.randomUUID().toString();if(joinedAt==null)joinedAt=LocalDateTime.now();}
 public String getId(){return id;} public void setId(String v){id=v;}
 public String getGroupOrderId(){return groupOrderId;} public void setGroupOrderId(String v){groupOrderId=v;}
 public String getFarmerId(){return farmerId;} public void setFarmerId(String v){farmerId=v;}
 public Integer getQuantity(){return quantity;} public void setQuantity(Integer v){quantity=v;}
 public BigDecimal getTotalPrice(){return totalPrice;} public void setTotalPrice(BigDecimal v){totalPrice=v;}
 public LocalDateTime getJoinedAt(){return joinedAt;} public void setJoinedAt(LocalDateTime v){joinedAt=v;}
}
