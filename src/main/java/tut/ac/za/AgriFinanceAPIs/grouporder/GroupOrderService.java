package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository; import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProduct; import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProductRepository;
import tut.ac.za.AgriFinanceAPIs.grouporder.dto.*; import tut.ac.za.AgriFinanceAPIs.notification.NotificationService;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal; import java.util.List;

@Service
public class GroupOrderService {
 private final GroupOrderRepository orders; private final GroupOrderItemRepository items; private final SupplierProductRepository products; private final FarmerRepository farmers; private final NotificationService notifications;
 public GroupOrderService(GroupOrderRepository o,GroupOrderItemRepository i,SupplierProductRepository p,FarmerRepository f,NotificationService n){orders=o;items=i;products=p;farmers=f;notifications=n;}
 @Transactional
 public GroupOrder create(GroupOrderRequest r){
  SupplierProduct p=product(r.getProductId()); if(r.getTargetQuantity()==null||r.getTargetQuantity()<1)bad("targetQuantity must be greater than 0");
  BigDecimal d=r.getDiscountRate()==null?BigDecimal.ZERO:r.getDiscountRate();if(d.compareTo(BigDecimal.ZERO)<0||d.compareTo(BigDecimal.valueOf(100))>0)bad("discountRate must be between 0 and 100");
  GroupOrder o=new GroupOrder();o.setProductId(p.getId());o.setTargetQuantity(r.getTargetQuantity());o.setDiscountRate(d);o=orders.save(o);
  notifications.createForSupplier(p.getSupplierId(),o.getId(),"A new group order was created for "+p.getProductName());
  return o;
 }
 public List<GroupOrder> all(String status){return status==null||status.isBlank()?orders.findAll():orders.findAllByStatusOrderByCreatedAtDesc(status);}
 public GroupOrder get(String id){return orders.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Group order not found"));}
 public List<GroupOrderItem> items(String id){get(id);return this.items.findAllByGroupOrderIdOrderByJoinedAtAsc(id);}
 @Transactional
 public GroupOrder join(String id,JoinGroupOrderRequest r){
  GroupOrder o=get(id); if(!"open".equalsIgnoreCase(o.getStatus()))bad("Group order is not open");
  if(r.getFarmerId()==null||!farmers.existsById(r.getFarmerId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Farmer not found");
  if(r.getQuantity()==null||r.getQuantity()<1)bad("quantity must be greater than 0");
  if(items.existsByGroupOrderIdAndFarmerId(id,r.getFarmerId()))bad("Farmer has already joined this group order");
  int newQty=o.getCurrentQuantity()+r.getQuantity(); if(newQty>o.getTargetQuantity())bad("Quantity exceeds the group order target");
  SupplierProduct p=product(o.getProductId());
  BigDecimal unit=p.getPrice().multiply(BigDecimal.ONE.subtract(o.getDiscountRate().divide(BigDecimal.valueOf(100))));
  GroupOrderItem item=new GroupOrderItem();item.setGroupOrderId(id);item.setFarmerId(r.getFarmerId());item.setQuantity(r.getQuantity());item.setTotalPrice(unit.multiply(BigDecimal.valueOf(r.getQuantity())));items.save(item);
  o.setCurrentQuantity(newQty);
  if(newQty>=o.getTargetQuantity()){o.setStatus("completed");notifications.createForSupplier(p.getSupplierId(),o.getId(),"Group order "+o.getId()+" reached its target quantity.");}
  return orders.save(o);
 }
 @Transactional
 public GroupOrder close(String id){GroupOrder o=get(id);o.setStatus("closed");return orders.save(o);}
 private SupplierProduct product(String id){if(id==null||id.isBlank())bad("productId is required");return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}
 private void bad(String m){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
