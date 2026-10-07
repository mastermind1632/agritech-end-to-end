package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, String> {
    Optional<Farmer> findByContact(String contact);
}
