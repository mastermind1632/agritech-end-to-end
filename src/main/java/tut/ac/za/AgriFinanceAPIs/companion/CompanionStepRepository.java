package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanionStepRepository extends JpaRepository<CompanionStep, String> {
    List<CompanionStep> findByActiveTrueOrderByStageAscSortOrderAsc();

    List<CompanionStep> findAllByOrderByStageAscSortOrderAsc();
}
