package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {
    List<ExportTask> findByApplicationIdOrderByStageNumberAscIdAsc(Long applicationId);
    Optional<ExportTask> findByApplicationIdAndTaskKey(Long applicationId, String taskKey);
}
