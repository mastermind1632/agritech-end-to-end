package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository; import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProduct; import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProductRepository;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierRepository;
import tut.ac.za.AgriFinanceAPIs.grouporder.dto.*; import tut.ac.za.AgriFinanceAPIs.notification.NotificationService;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.math.RoundingMode; import java.time.LocalDate; import java.time.LocalDateTime; import java.util.List;

@Service
public class GroupOrderService {
 private final GroupOrderRepository orders; private final GroupOrderItemRepository items; private final SupplierProductRepository products; private final FarmerRepository farmers; private final NotificationService notifications;
 private final SupplierRepository suppliers; private final NotificationHub hub; private final AuditService audit;
 public GroupOrderService(GroupOrderRepository o,GroupOrderItemRepository i,SupplierProductRepository p,FarmerRepository f,NotificationService n,
                          SupplierRepository s,NotificationHub h,AuditService a){orders=o;items=i;products=p;farmers=f;notifications=n;suppliers=s;hub=h;audit=a;}

 @Transactional
 public GroupOrder create(GroupOrderRequest r, String creatorId){
  SupplierProduct p=product(r.getProductId()); if(r.getTargetQuantity()==null||r.getTargetQuantity()<1)bad("targetQuantity must be greater than 0");
  if(r.getTargetQuantity()>1_000_000)bad("targetQuantity is too large");
  BigDecimal d=r.getDiscountRate()==null?BigDecimal.ZERO:r.getDiscountRate();if(d.compareTo(BigDecimal.ZERO)<0||d.compareTo(BigDecimal.valueOf(100))>0)bad("discountRate must be between 0 and 100");
  if(r.getDeadline()!=null && r.getDeadline().isBefore(LocalDate.now()))bad("The deadline cannot be in the past");
  GroupOrder o=new GroupOrder();o.setProductId(p.getId());o.setTargetQuantity(r.getTargetQuantity());o.setDiscountRate(d);o.setCreatedBy(creatorId);
  if(r.getDeadline()!=null)o.setDeadline(r.getDeadline().atTime(23,59,59));
  o=orders.save(o);
  notifications.createForSupplier(p.getSupplierId(),o.getId(),"A new group order was created for "+p.getProductName());
  audit.record(creatorId,"GROUP_ORDER_CREATED","GROUP_ORDER",o.getId(),p.getProductName()+" target "+r.getTargetQuantity());
  return o;
 }
 /** Kept for older callers: creator unknown. */
 public GroupOrder create(GroupOrderRequest r){return create(r,null);}
 public List<GroupOrder> all(String status){return status==null||status.isBlank()?orders.findAll():orders.findAllByStatusOrderByCreatedAtDesc(status);}
 public GroupOrder get(String id){return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Group order not found"));}
 public List<GroupOrderItem> items(String id){get(id);return this.items.findAllByGroupOrderIdOrderByJoinedAtAsc(id);}

 @Transactional
 public GroupOrder join(String id,JoinGroupOrderRequest r){
  GroupOrder o=orders.findForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Group order not found"));
  if(!"open".equalsIgnoreCase(o.getStatus()))bad("Group order is not open");
  if(o.getDeadline()!=null && o.getDeadline().isBefore(LocalDateTime.now()))bad("This group order has passed its deadline");
  if(r.getFarmerId()==null||!farmers.existsById(r.getFarmerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Farmer not found");
  if(r.getQuantity()==null||r.getQuantity()<1)bad("quantity must be greater than 0");
  if(items.existsByGroupOrderIdAndFarmerId(id,r.getFarmerId()))bad("Farmer has already joined this group order");
  int remaining=o.getTargetQuantity()-o.getCurrentQuantity();
  if(r.getQuantity()>remaining)bad("Only "+remaining+" units are still available in this group order");
  int newQty=o.getCurrentQuantity()+r.getQuantity();
  SupplierProduct p=product(o.getProductId());
  BigDecimal unit=p.getPrice().multiply(BigDecimal.ONE.subtract(o.getDiscountRate().divide(BigDecimal.valueOf(100),6,RoundingMode.HALF_UP)));
  GroupOrderItem item=new GroupOrderItem();item.setGroupOrderId(id);item.setFarmerId(r.getFarmerId());item.setQuantity(r.getQuantity());
  item.setTotalPrice(unit.multiply(BigDecimal.valueOf(r.getQuantity())).setScale(2,RoundingMode.HALF_UP));items.save(item);
  o.setCurrentQuantity(newQty);
  if(newQty>=o.getTargetQuantity()){
   o.setStatus("completed");o.setClosedAt(LocalDateTime.now());
   notifications.createForSupplier(p.getSupplierId(),o.getId(),"Group order "+o.getId()+" reached its target quantity.");
   for(GroupOrderItem member: items.findAllByGroupOrderIdOrderByJoinedAtAsc(id)){
    hub.notify(member.getFarmerId(),"GROUP","Group order complete: "+p.getProductName(),
      "The group reached its target of "+o.getTargetQuantity()+" units and the supplier has been notified.","groups");
   }
  }
  audit.record(r.getFarmerId(),"GROUP_ORDER_JOINED","GROUP_ORDER",id,r.getQuantity()+" units");
  return orders.save(o);
 }

 /** Kept for older callers; only usable internally. */
 @Transactional
 public GroupOrder close(String id){GroupOrder o=get(id);o.setStatus("closed");o.setClosedAt(LocalDateTime.now());return orders.save(o);}

 /** The creator, the supplier who owns the product, or an admin may close a group order. */
 @Transactional
 public GroupOrder close(String id,String userId,boolean admin){
  GroupOrder o=orders.findForUpdate(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Group order not found"));
  SupplierProduct p=product(o.getProductId());
  String supplierOwner=suppliers.findById(p.getSupplierId()).map(s->s.getOwnerId()).orElse(null);
  boolean allowed=admin||userId.equals(o.getCreatedBy())||userId.equals(supplierOwner);
  if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the person who started this group order, the supplier or an admin can close it");
  if(!"open".equalsIgnoreCase(o.getStatus()))bad("Group order is already "+o.getStatus());
  o.setStatus("closed");o.setClosedAt(LocalDateTime.now());
  for(GroupOrderItem member: items.findAllByGroupOrderIdOrderByJoinedAtAsc(id)){
   hub.notify(member.getFarmerId(),"GROUP","Group order closed: "+p.getProductName(),"It was closed before reaching its target ("+o.getCurrentQuantity()+" of "+o.getTargetQuantity()+").","groups");
  }
  audit.record(userId,"GROUP_ORDER_CLOSED","GROUP_ORDER",id,null);
  return orders.save(o);
 }

 /** Every 10 minutes: open group orders past their deadline become "expired" and members are told. */
 @Scheduled(fixedDelay=600_000L, initialDelay=60_000L)
 @Transactional
 public void expireOverdue(){
  for(GroupOrder o: orders.findByStatusAndDeadlineBefore("open",LocalDateTime.now())){
   o.setStatus("expired");o.setClosedAt(LocalDateTime.now());orders.save(o);
   String name=products.findById(o.getProductId()).map(SupplierProduct::getProductName).orElse("a product");
   for(GroupOrderItem member: items.findAllByGroupOrderIdOrderByJoinedAtAsc(o.getId())){
    hub.notify(member.getFarmerId(),"GROUP","Group order expired: "+name,"The deadline passed with "+o.getCurrentQuantity()+" of "+o.getTargetQuantity()+" units committed.","groups");
   }
  }
 }
 private SupplierProduct product(String id){if(id==null||id.isBlank())bad("productId is required");return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}
 private void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
