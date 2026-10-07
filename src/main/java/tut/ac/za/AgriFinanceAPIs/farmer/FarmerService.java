package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FarmerService {
    private final FarmerRepository farmerRepository;
    private final PasswordEncoder passwordEncoder;

    public FarmerService(FarmerRepository farmerRepository, PasswordEncoder passwordEncoder) {
        this.farmerRepository = farmerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Farmer register(String name, String location, String contact, String password) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        if (contact == null || contact.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact is required");
        if (password == null || password.length() < 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        if (farmerRepository.findByContact(contact.trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact already registered");
        }

        Farmer farmer = new Farmer();
        farmer.setName(name.trim());
        farmer.setLocation(location);
        farmer.setContact(contact.trim());
        farmer.setPasswordHash(passwordEncoder.encode(password));
        return farmerRepository.save(farmer);
    }

    public Farmer login(String contact, String password) {
        if (contact == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact and password are required");
        }
        Farmer farmer = farmerRepository.findByContact(contact.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid contact or password"));
        if (!passwordEncoder.matches(password, farmer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid contact or password");
        }
        return farmer;
    }

    public Farmer getById(String id) {
        return farmerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found"));
    }
}
