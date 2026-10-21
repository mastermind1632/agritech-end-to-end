package tut.ac.za.AgriFinanceAPIs.companion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CompanionDtos {
    private CompanionDtos() { }

    public record ProfileRequest(String legalStructure, String businessName, String registrationNumber, LocalDate registeredOn,
                                 String taxNumber, Boolean vatRegistered, String vatNumber, Integer employees,
                                 String turnoverBand, String crops, BigDecimal hectares, Boolean livestock,
                                 String waterSource, Boolean irrigates, Boolean usesAgrochemicals, Boolean sellsToRetailers,
                                 Boolean exportInterest, Boolean hasBusinessAccount, Integer financialYearEndMonth,
                                 String province) { }

    public record ProfileView(String legalStructure, String businessName, String registrationNumber, LocalDate registeredOn,
                              String taxNumber, boolean vatRegistered, String vatNumber, int employees, String turnoverBand,
                              String crops, BigDecimal hectares, boolean livestock, String waterSource, boolean irrigates,
                              boolean usesAgrochemicals, boolean sellsToRetailers, boolean exportInterest,
                              boolean hasBusinessAccount, int financialYearEndMonth, String province, boolean verified,
                              LocalDateTime verifiedAt, boolean intakeCompleted, Map<String, List<String>> options) { }

    public record StepView(String code, int stage, String stageName, String title, String description, String why,
                           String authority, String linkUrl, String linkLabel, String moduleLink, String status,
                           String note, String documentId, String documentTitle, LocalDateTime completedAt,
                           String reviewStatus, LocalDate lastVerified) { }

    public record StageView(int stage, String name, int done, int applicable, int percent, List<StepView> steps) { }

    public record Checklist(int percent, int done, int applicable, boolean intakeCompleted, List<StageView> stages,
                            String disclaimer) { }

    public record TaskUpdate(String status, String note, String documentId) { }

    public record DeadlineView(Long id, String code, String title, LocalDate dueDate, long daysLeft, String authority,
                               String sourceUrl, boolean estimated, boolean custom, String status, String documentId,
                               String kind) { }

    public record DeadlineRequest(String title, LocalDate dueDate, String authority, String documentId) { }

    public record DeadlineStatus(String status) { }

    public record CropPlanRequest(String crop, BigDecimal hectares, BigDecimal yieldPerHa, String yieldUnit,
                                  BigDecimal expectedPrice, BigDecimal costPerHa, Integer plantMonth, Integer harvestMonth) { }

    public record CropPlanView(Long id, String crop, BigDecimal hectares, BigDecimal yieldPerHa, String yieldUnit,
                               BigDecimal expectedPrice, BigDecimal costPerHa, int plantMonth, int harvestMonth,
                               BigDecimal marketPrice, String marketPriceUnit, BigDecimal actualCostPerHa) { }

    public record PlanView(List<CropPlanView> crops, CashflowProjector.Projection projection, BigDecimal monthlyOverheads,
                           BigDecimal openingCash, List<String> assumptions) { }

    public record StepEdit(String title, String description, String why, String authority, String ruleKey,
                           String linkUrl, String linkLabel, Boolean active) { }

    public record Summary(int percent, int done, int applicable, boolean intakeCompleted, boolean verified,
                          long upcomingDeadlines, DeadlineView nextDeadline) { }
}
