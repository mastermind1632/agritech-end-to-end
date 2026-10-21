package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.grouporder.GroupOrderRepository;
import tut.ac.za.AgriFinanceAPIs.supplier.dto.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Supplier catalogues. Anyone may browse. Only SUPPLIER accounts create catalogues, and only the owner
 * (or an admin) may edit or delete a supplier or its products.
 */
@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierProductRepository products;
    private final GroupOrderRepository groupOrders;
    private final AuditService audit;

    public SupplierService(SupplierRepository suppliers, SupplierProductRepository products,
                           GroupOrderRepository groupOrders, AuditService audit){
        this.suppliers=suppliers;this.products=products;this.groupOrders=groupOrders;this.audit=audit;
    }

    @Transactional
    public Supplier create(SupplierRequest r, String ownerId){
        require(r.getName(),"Name is required");
        Supplier s=new Supplier(); s.setName(r.getName().trim()); s.setLocation(r.getLocation()); s.setContact(r.getContact()); s.setOwnerId(ownerId);
        Supplier saved=suppliers.save(s);
        audit.record(ownerId,"SUPPLIER_CREATED","SUPPLIER",saved.getId(),saved.getName());
        return saved;
    }
    public List<Supplier> all(){return suppliers.findAll();}
    public List<Supplier> mine(String ownerId){return suppliers.findByOwnerId(ownerId);}
    public Supplier get(String id){return suppliers.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Supplier not found"));}

    /** 403 unless the user owns the supplier or is an admin. */
    public Supplier owned(String id, String userId, boolean admin){
        Supplier s=get(id);
        if(!admin && (s.getOwnerId()==null || !s.getOwnerId().equals(userId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the supplier who owns this catalogue can change it");
        return s;
    }

    @Transactional
    public Supplier update(String id,SupplierRequest r,String userId,boolean admin){
        Supplier s=owned(id,userId,admin);require(r.getName(),"Name is required");
        s.setName(r.getName().trim());s.setLocation(r.getLocation());s.setContact(r.getContact());
        audit.record(userId,"SUPPLIER_UPDATED","SUPPLIER",id,null);
        return suppliers.save(s);
    }

    @Transactional
    public void delete(String id,String userId,boolean admin){
        Supplier s=owned(id,userId,admin);
        for(SupplierProduct p: products.findAllBySupplierId(id)){
            if(groupOrders.existsByProductId(p.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This supplier has group orders. Close them and remove the products instead of deleting the supplier.");
        }
        suppliers.delete(s);
        audit.record(userId,"SUPPLIER_DELETED","SUPPLIER",id,s.getName());
    }

    @Transactional
    public SupplierProduct addProduct(String supplierId,ProductRequest r,String userId,boolean admin){
        owned(supplierId,userId,admin); require(r.getProductName(),"Product name is required");
        if(r.getPrice()==null||r.getPrice().compareTo(BigDecimal.ZERO)<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or greater");
        SupplierProduct p=new SupplierProduct();p.setSupplierId(supplierId);p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());setComparison(p,r);return products.save(p);
    }
    public List<SupplierProduct> products(String supplierId){get(supplierId);return products.findAllBySupplierId(supplierId);}
    public SupplierProduct getProduct(String id){return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}

    @Transactional
    public SupplierProduct updateProduct(String id,ProductRequest r,String userId,boolean admin){
        SupplierProduct p=getProduct(id);owned(p.getSupplierId(),userId,admin);
        require(r.getProductName(),"Product name is required");
        if(r.getPrice()==null||r.getPrice().compareTo(BigDecimal.ZERO)<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or greater");
        p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());setComparison(p,r);return products.save(p);
    }
    static void setComparison(SupplierProduct p,ProductRequest r){
        // Legacy clients that omit all fields preserve existing comparison metadata.
        if(r.getComparisonKey()==null && r.getPackSize()==null && r.getPackUnit()==null)return;
        if(r.getComparisonKey()!=null && r.getComparisonKey().isBlank() && r.getPackSize()==null
                && (r.getPackUnit()==null || r.getPackUnit().isBlank())){
            p.setComparisonKey(null);p.setPackSize(null);p.setPackUnit(null);return;
        }
        if(r.getComparisonKey()==null || r.getComparisonKey().isBlank() || r.getComparisonKey().length()>150
                || r.getPackSize()==null || r.getPackSize().signum()<=0 || r.getPackSize().scale()>3
                || r.getPackSize().compareTo(new BigDecimal("999999999.999"))>0
                || !java.util.Set.of("kg","l","unit").contains(r.getPackUnit()==null?"":r.getPackUnit()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide a product specification code, positive pack size (up to 3 decimals), and kg, l or unit");
        p.setComparisonKey(r.getComparisonKey().trim());p.setPackSize(r.getPackSize());p.setPackUnit(r.getPackUnit());
    }

    @Transactional
    public void deleteProduct(String id,String userId,boolean admin){
        SupplierProduct p=getProduct(id);owned(p.getSupplierId(),userId,admin);
        if(groupOrders.existsByProductId(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This product has group orders and cannot be deleted. Change its price or name instead.");
        products.delete(p);
    }
    private void require(String v,String m){if(v==null||v.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
