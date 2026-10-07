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
        SupplierProduct p=new SupplierProduct();p.setSupplierId(supplierId);p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());return products.save(p);
    }
    public List<SupplierProduct> products(String supplierId){get(supplierId);return products.findAllBySupplierId(supplierId);}
    public SupplierProduct getProduct(String id){return products.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));}
    public SupplierProduct updateProduct(String id,ProductRequest r){SupplierProduct p=getProduct(id);require(r.getProductName(),"Product name is required");if(r.getPrice()==null||r.getPrice().compareTo(BigDecimal.ZERO)<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Price must be zero or greater");p.setProductName(r.getProductName().trim());p.setPrice(r.getPrice());return products.save(p);}
    public void deleteProduct(String id){products.delete(getProduct(id));}
    private void require(String v,String m){if(v==null||v.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
