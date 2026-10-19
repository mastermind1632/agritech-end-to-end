package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportDocumentRepository extends JpaRepository<ExportDocument, Long> {
    List<ExportDocument> findByApplicationIdOrderByUploadedAtDesc(Long applicationId);
    long countByApplicationIdAndDocumentType(Long applicationId, String documentType);
    long countByApplicationIdAndStageNumberAndDocumentType(Long applicationId, Integer stageNumber, String documentType);
}
