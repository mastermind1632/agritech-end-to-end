package tut.ac.za.AgriFinanceAPIs.recommendation;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="ai_recommendations")
public class AiRecommendation {
 @Id @Column(length=36) private String id;
 @Column(name="farmer_id",nullable=false,length=36) private String farmerId;
 @Column(name="recommended_product_id",length=36) private String recommendedProductId;
 @Column(name="suggested_group_order_id",length=36) private String suggestedGroupOrderId;
 @Column(nullable=false,columnDefinition="TEXT") private String reason;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist public void prePersist(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=LocalDateTime.now();}
 public String getId(){return id;} public void setId(String v){id=v;} public String getFarmerId(){return farmerId;} public void setFarmerId(String v){farmerId=v;}
 public String getRecommendedProductId(){return recommendedProductId;} public void setRecommendedProductId(String v){recommendedProductId=v;}
 public String getSuggestedGroupOrderId(){return suggestedGroupOrderId;} public void setSuggestedGroupOrderId(String v){suggestedGroupOrderId=v;}
 public String getReason(){return reason;} public void setReason(String v){reason=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
