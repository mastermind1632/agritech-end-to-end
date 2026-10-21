package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.supplier.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service){this.service=service;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Supplier create(@RequestBody SupplierRequest r, Authentication auth){
        Roles.require(auth, Roles.SUPPLIER);
        return service.create(r, auth.getName());
    }
    @GetMapping public List<Supplier> all(){return service.all();}
    /** The catalogues this supplier account manages. (GET /api/suppliers/** is public, so check the login here.) */
    @GetMapping("/mine") public List<Supplier> mine(Authentication auth){
        if(auth==null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please sign in");
        return Roles.isAdmin(auth) ? service.all() : service.mine(auth.getName());
    }
    @GetMapping("/{id}") public Supplier get(@PathVariable String id){return service.get(id);}
    @PutMapping("/{id}") public Supplier update(@PathVariable String id,@RequestBody SupplierRequest r, Authentication auth){return service.update(id,r,auth.getName(),Roles.isAdmin(auth));}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id, Authentication auth){service.delete(id,auth.getName(),Roles.isAdmin(auth));}
    @PostMapping("/{supplierId}/products") @ResponseStatus(HttpStatus.CREATED) public SupplierProduct addProduct(@PathVariable String supplierId,@RequestBody ProductRequest r, Authentication auth){return service.addProduct(supplierId,r,auth.getName(),Roles.isAdmin(auth));}
    @GetMapping("/{supplierId}/products") public List<SupplierProduct> products(@PathVariable String supplierId){return service.products(supplierId);}
    @PutMapping("/products/{productId}") public SupplierProduct updateProduct(@PathVariable String productId,@RequestBody ProductRequest r, Authentication auth){return service.updateProduct(productId,r,auth.getName(),Roles.isAdmin(auth));}
    @DeleteMapping("/products/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable String productId, Authentication auth){service.deleteProduct(productId,auth.getName(),Roles.isAdmin(auth));}
}
