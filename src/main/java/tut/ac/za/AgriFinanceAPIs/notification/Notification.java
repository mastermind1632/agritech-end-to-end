package tut.ac.za.AgriFinanceAPIs.notification;
import jakarta.persistence.*; import java.time.LocalDateTime; import java.util.UUID;
@Entity @Table(name="notifications")
public class Notification {
 @Id @Column(length=36) private String id;
 @Column(name="supplier_id",nullable=false,length=36) private String supplierId;
 @Column(name="group_order_id",length=36) private String groupOrderId;
 @Column(nullable=false,columnDefinition="TEXT") private String message;
 @Column(name="is_read",nullable=false) private boolean read=false;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist public void prePersist(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=LocalDateTime.now();}
 public String getId(){return id;} public void setId(String v){id=v;} public String getSupplierId(){return supplierId;} public void setSupplierId(String v){supplierId=v;}
 public String getGroupOrderId(){return groupOrderId;} public void setGroupOrderId(String v){groupOrderId=v;} public String getMessage(){return message;} public void setMessage(String v){message=v;}
 public boolean isRead(){return read;} public void setRead(boolean v){read=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
