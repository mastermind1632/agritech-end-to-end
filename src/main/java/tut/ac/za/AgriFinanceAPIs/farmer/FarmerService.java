package tut.ac.za.AgriFinanceAPIs.farmer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.compliance.BusinessCompliance;
import tut.ac.za.AgriFinanceAPIs.compliance.BusinessComplianceRepository;

@Service
public class FarmerService {
    private static final Logger log = LoggerFactory.getLogger(FarmerService.class);

    private final FarmerRepository farmerRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessComplianceRepository complianceRepository;

    public FarmerService(FarmerRepository farmerRepository,
                         PasswordEncoder passwordEncoder,
                         BusinessComplianceRepository complianceRepository) {
        this.farmerRepository = farmerRepository;
        this.passwordEncoder = passwordEncoder;
        this.complianceRepository = complianceRepository;
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
        Farmer saved = farmerRepository.save(farmer);

        // Create an empty business-compliance profile so the reminder backfill
        // scheduler can find this farmer on its next run, even if they never
        // open the compliance screen. Failure here must not block registration.
        try {
            BusinessCompliance bc = new BusinessCompliance();
            bc.setFarmerId(saved.getId());
            complianceRepository.save(bc);
        } catch (Exception ex) {
            log.warn("Could not create business-compliance row for new farmer {}: {}", saved.getId(), ex.getMessage());
        }

        return saved;
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
