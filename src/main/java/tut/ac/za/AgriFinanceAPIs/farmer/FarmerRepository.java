package tut.ac.za.AgriFinanceAPIs.farmer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, String> {
    Optional<Farmer> findByContact(String contact);

    Optional<Farmer> findByEmailIgnoreCase(String email);

    List<Farmer> findByRole(String role);

    long countByRole(String role);

    @Query("select f from Farmer f where (:q = '' or lower(f.name) like :q or lower(f.contact) like :q or lower(coalesce(f.email, '')) like :q) "
            + "and (:role = '' or f.role = :role) order by f.createdAt desc")
    Page<Farmer> search(@Param("q") String q, @Param("role") String role, Pageable pageable);
}
