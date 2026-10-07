package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.supplier.dto.*;
import java.math.BigDecimal;
import java.util.List;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierProductRepository products;
    public SupplierService(SupplierRepository suppliers, SupplierProductRepository products){this.suppliers=suppliers;this.products=products;}

    public Supplier create(SupplierRequest r){
        require(r.getName(),"Name is required");
        Supplier s=new Supplier(); s.setName(r.getName().trim()); s.setLocation(r.getLocation()); s.setContact(r.getContact()); return suppliers.save(s);
    }
    public List<Supplier> all(){return suppliers.findAll();}
    public Supplier get(String id){return suppliers.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Supplier not found"));}
    public Supplier update(String id,SupplierRequest r){Supplier s=get(id);require(r.getName(),"Name is required");s.setName(r.getName().trim());s.setLocation(r.getLocation());s.setContact(r.getContact());return suppliers.save(s);}
    public void delete(String id){suppliers.delete(get(id));}

    public SupplierProduct addProduct(String supplierId,ProductRequest r){
        get(supplierId); require(r.getProductName(),"Product name is required");
        if(r.getPrice()==null||r.getPrice().compareTo(BigDecimal.ZERO)<0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or greater");
        SupplierProduct p=new SupplierProduct();p.setSupplierId(supplierId);p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());setComparison(p,r);return products.save(p);
    }
    public List<SupplierProduct> products(String supplierId){get(supplierId);return products.findAllBySupplierId(supplierId);}
    public SupplierProduct getProduct(String id){return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}
    public SupplierProduct updateProduct(String id,ProductRequest r){SupplierProduct p=getProduct(id);require(r.getProductName(),"Product name is required");if(r.getPrice()==null||r.getPrice().compareTo(BigDecimal.ZERO)<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or greater");p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());setComparison(p,r);return products.save(p);}
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
    public void deleteProduct(String id){products.delete(getProduct(id));}
    private void require(String v,String m){if(v==null||v.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
