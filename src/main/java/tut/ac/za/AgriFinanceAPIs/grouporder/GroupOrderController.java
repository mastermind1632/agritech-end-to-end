package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import org.springframework.security.core.Authentication; import tut.ac.za.AgriFinanceAPIs.grouporder.dto.*; import java.util.List;
@RestController @RequestMapping("/api/group-orders")
public class GroupOrderController {
 private final GroupOrderService service; public GroupOrderController(GroupOrderService s){service=s;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public GroupOrder create(@RequestBody GroupOrderRequest r){return service.create(r);}
 @GetMapping public List<GroupOrder> all(@RequestParam(required=false) String status){return service.all(status);}
 @GetMapping("/{id}") public GroupOrder get(@PathVariable String id){return service.get(id);}
 @GetMapping("/{id}/items") public List<GroupOrderItem> items(@PathVariable String id){return service.items(id);}
 @PostMapping("/{id}/join") @ResponseStatus(HttpStatus.CREATED) public GroupOrder join(@PathVariable String id,@RequestBody JoinGroupOrderRequest r, Authentication auth){r.setFarmerId(auth.getName()); return service.join(id,r);}
 @PutMapping("/{id}/close") public GroupOrder close(@PathVariable String id){return service.close(id);}
}
