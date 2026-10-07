package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.supplier.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service){this.service=service;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Supplier create(@RequestBody SupplierRequest r){return service.create(r);}
    @GetMapping public List<Supplier> all(){return service.all();}
    @GetMapping("/{id}") public Supplier get(@PathVariable String id){return service.get(id);}
    @PutMapping("/{id}") public Supplier update(@PathVariable String id,@RequestBody SupplierRequest r){return service.update(id,r);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id){service.delete(id);}
    @PostMapping("/{supplierId}/products") @ResponseStatus(HttpStatus.CREATED) public SupplierProduct addProduct(@PathVariable String supplierId,@RequestBody ProductRequest r){return service.addProduct(supplierId,r);}
    @GetMapping("/{supplierId}/products") public List<SupplierProduct> products(@PathVariable String supplierId){return service.products(supplierId);}
    @PutMapping("/products/{productId}") public SupplierProduct updateProduct(@PathVariable String productId,@RequestBody ProductRequest r){return service.updateProduct(productId,r);}
    @DeleteMapping("/products/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable String productId){service.deleteProduct(productId);}
}
