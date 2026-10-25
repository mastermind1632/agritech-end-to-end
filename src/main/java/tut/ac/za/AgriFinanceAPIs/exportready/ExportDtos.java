package tut.ac.za.AgriFinanceAPIs.exportready;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ExportDtos {
    private ExportDtos() { }

    public record StartWorkflow(
    String productCode, 
    String destinationCode, 
    LocalDate targetShipDate, 
    BigDecimal volume, 
    String volumeUnit,
    
    // --- SOP Specific Fields ---
    String fboCode,
    String variety,
    String classCategory,
    Integer packageCount,
    String packageType,
    Integer palletCount,
    String containerNumber,
    String transportMode // SEA, AIR, LAND
) { }

    public record ItemUpdate(String status, String note, String documentId) { }

    public record CloseWorkflow(String outcome, String lessons, boolean cancelled) { }

    public record RequirementView(Long id, String code, int stage, String stageName, String authority, String authorityLabel,
                                  String title, String description, String documents, Integer leadTimeDays, String estCost,
                                  String sourceUrl, String reviewStatus, LocalDate lastVerified, String productCode,
                                  String destinationCode, int version) { }

    public record ItemView(Long itemId, RequirementView requirement, String status, String note, String documentId,
                           String documentTitle, LocalDateTime completedAt, boolean changedSinceStarted, boolean autoChecked) { }

    public record StageView(int stage, String name, int percent, boolean current, boolean complete, List<ItemView> items) { }

    public record WorkflowSummary(String id, String productCode, String productName, String hsCode, String destinationCode,
                                  String destinationName, int currentStage, String currentStageName, String status,
                                  int readiness, boolean exportReady, LocalDate targetShipDate, LocalDateTime createdAt,
                                  long changedRequirements) { }

    public record WorkflowDetail(WorkflowSummary summary, List<StageView> stages, Map<String, List<ItemView>> byAuthority,
                                 Map<String, Integer> authorityPercent, boolean canAdvance, List<String> blockers,
                                 String outcome, String lessons, BigDecimal volume, String volumeUnit, String disclaimer) { }

    public record Options(List<ExportProduct> products, List<ExportDestination> destinations, Map<String, String> authorities,
                          List<String> stages) { }

    public record RequirementEdit(String title, String description, String documents, Integer leadTimeDays, String estCost,
                                  String sourceUrl, Boolean active, String changeSummary) { }

    public record ChangeProposal(Long requirementId, String summary, String proposedTitle, String proposedDescription) { }

    public record ChangeView(Long id, Long requirementId, String requirementTitle, String source, String summary,
                             String proposedTitle, String proposedDescription, String status, LocalDateTime createdAt) { }
}

