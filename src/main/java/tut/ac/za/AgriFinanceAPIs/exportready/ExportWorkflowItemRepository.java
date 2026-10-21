package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExportWorkflowItemRepository extends JpaRepository<ExportWorkflowItem, Long> {
    List<ExportWorkflowItem> findByWorkflowId(String workflowId);

    List<ExportWorkflowItem> findByWorkflowIdIn(Collection<String> workflowIds);

    Optional<ExportWorkflowItem> findByWorkflowIdAndRequirementId(String workflowId, Long requirementId);

    List<ExportWorkflowItem> findByRequirementId(Long requirementId);
}
