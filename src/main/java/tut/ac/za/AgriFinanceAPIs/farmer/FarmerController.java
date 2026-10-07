package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.FarmerResponse;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.LoginRequest;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.RegisterRequest;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.AuthResponse;
import tut.ac.za.AgriFinanceAPIs.security.JwtService;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService farmerService;
    private final JwtService jwtService;

    public FarmerController(FarmerService farmerService, JwtService jwtService) {
        this.farmerService = farmerService; this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody RegisterRequest request) {
        var farmer = farmerService.register(request.getName(), request.getLocation(), request.getContact(), request.getPassword());
        return new AuthResponse(jwtService.createToken(farmer.getId(), farmer.getContact()), new FarmerResponse(farmer));
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        var farmer = farmerService.login(request.getContact(), request.getPassword());
        return new AuthResponse(jwtService.createToken(farmer.getId(), farmer.getContact()), new FarmerResponse(farmer));
    }

    @GetMapping("/{id}")
    public FarmerResponse getFarmer(@PathVariable String id, Authentication authentication) {
        if (!id.equals(authentication.getName())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "You can only access your own profile");
        return new FarmerResponse(farmerService.getById(id));
    }
}
