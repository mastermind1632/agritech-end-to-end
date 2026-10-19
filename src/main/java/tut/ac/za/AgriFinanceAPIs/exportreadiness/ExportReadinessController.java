package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/exports")
public class ExportReadinessController {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> TASK_STATUSES = Set.of(
            "NOT_STARTED", "IN_PROGRESS", "AWAITING_DOCUMENTS", "SUBMITTED",
            "EVIDENCE_CHECKED", "ACTION_REQUIRED", "COMPLETED", "NOT_APPLICABLE");
    private static final Set<String> REGISTRATION_STATUSES = Set.of(
            "NOT_ASSESSED", "APPLICABLE", "NOT_APPLICABLE", "IN_PROGRESS", "SUBMITTED");
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "BUSINESS_REGISTRATION", "MARKET_ACCESS", "PRODUCTION_RECORD", "CONSIGNMENT_NOTE",
            "INSPECTION_RECORD", "OFFICIAL_CERTIFICATE", "COMMERCIAL_DOCUMENT",
            "CUSTOMS_CLEARANCE", "OTHER");
    private static final Set<String> DOCUMENT_STATUSES = Set.of(
            "UPLOADED", "AWAITING_INTERNAL_REVIEW", "CHECKED_FOR_INTERNAL_TRACKING");
    private static final Set<String> CONSIGNMENT_FIELDS = Set.of(
            "exporterName", "productionUnitCode", "productionArea", "commodity", "variety",
            "classCategory", "destinationCountry", "dischargePort", "packageCount", "packageType",
            "palletCount", "inspectionDate", "inspectionPoint", "consignmentNoteReference",
            "grossWeight", "netWeight", "containerNumber", "palletIdentificationNumbers",
            "certificationOffice", "bookingReference", "regimeCode", "orchardBlockNumber",
            "phytosanitaryInformation", "certificateIssued", "certificateType", "certificateNumber",
            "certificateIssueDate", "certificateValidity", "certificateStatus",
            "certificateConfirmationSource", "inspectionResult", "notes");
    private static final List<StageTask> STAGE_TASKS = List.of(
            new StageTask("eligibility-product", 1, "Record the product, variety, quantity, location and destination"),
            new StageTask("eligibility-buyer", 1, "Record buyer details and check which registrations apply"),
            new StageTask("eligibility-classification", 1, "Ask SARS or a customs professional to confirm the tariff classification"),
            new StageTask("market-access-status", 2, "Confirm market access for this exact product, origin and destination"),
            new StageTask("market-access-conditions", 2, "Ask which import conditions, permits, treatments or residue limits apply"),
            new StageTask("registrations-authorities", 3, "Confirm which exporter, farm, orchard or packhouse registrations apply"),
            new StageTask("registrations-evidence", 3, "Record registration evidence and the authority that confirmed it"),
            new StageTask("production-records", 4, "Organise applicable input, spray, pest-monitoring and harvest records"),
            new StageTask("production-traceability", 4, "Check traceability records and any buyer or private-standard evidence"),
            new StageTask("shipment-plan", 5, "Plan packaging, labels, transport, quantities and cold-chain arrangements where relevant"),
            new StageTask("shipment-documents", 5, "Prepare applicable commercial documents and consignment details"),
            new StageTask("inspection-arrangement", 6, "Confirm the applicable inspection and certification process with the responsible authority"),
            new StageTask("inspection-result", 6, "Record actual inspection results and certificates received; do not enter unissued details"),
            new StageTask("customs-process", 7, "Confirm exporter, declaration and customs steps with SARS or a customs professional"),
            new StageTask("customs-evidence", 7, "Collect applicable declaration, origin and clearance evidence"),
            new StageTask("shipment-outcome", 8, "Record the actual shipment outcome, date and final consignment reference"),
            new StageTask("shipment-learning", 8, "Record unresolved issues, evidence and lessons for next time"));

    private static final Map<String, Integer> CONSIGNMENT_FIELD_LIMITS = Map.ofEntries(
            Map.entry("exporterName", 200), Map.entry("productionUnitCode", 120), Map.entry("productionArea", 200),
            Map.entry("commodity", 120), Map.entry("variety", 120), Map.entry("classCategory", 100),
            Map.entry("destinationCountry", 100), Map.entry("dischargePort", 120), Map.entry("packageCount", 80),
            Map.entry("packageType", 100), Map.entry("palletCount", 80), Map.entry("inspectionDate", 40),
            Map.entry("inspectionPoint", 160), Map.entry("consignmentNoteReference", 160),
            Map.entry("grossWeight", 80), Map.entry("netWeight", 80), Map.entry("containerNumber", 100),
            Map.entry("palletIdentificationNumbers", 1000), Map.entry("certificationOffice", 160),
            Map.entry("bookingReference", 160), Map.entry("regimeCode", 100),
            Map.entry("orchardBlockNumber", 120), Map.entry("phytosanitaryInformation", 1200),
            Map.entry("certificateIssued", 10), Map.entry("certificateType", 120),
            Map.entry("certificateNumber", 160), Map.entry("certificateIssueDate", 40),
            Map.entry("certificateValidity", 200), Map.entry("certificateStatus", 120),
            Map.entry("certificateConfirmationSource", 400), Map.entry("inspectionResult", 1200),
            Map.entry("notes", 2000));
    private static final Set<String> CERTIFICATE_FIELDS = Set.of(
            "certificateIssued", "certificateType", "certificateNumber", "certificateIssueDate",
            "certificateValidity", "certificateStatus", "certificateConfirmationSource");

    private final ExportApplicationRepository applications;
    private final ExportTaskRepository tasks;
    private final ExportRequirementRepository requirements;
    private final ExportDocumentRepository documents;
    private final ExportConsignmentRepository consignments;
    private final ObjectMapper objectMapper;

    public ExportReadinessController(ExportApplicationRepository applications, ExportTaskRepository tasks,
                                     ExportRequirementRepository requirements, ExportDocumentRepository documents,
                                     ExportConsignmentRepository consignments, ObjectMapper objectMapper) {
        this.applications = applications;
        this.tasks = tasks;
        this.requirements = requirements;
        this.documents = documents;
        this.consignments = consignments;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/applications")
    public List<Map<String, Object>> list(Authentication auth) {
        return applications.findByFarmerIdOrderByUpdatedAtDesc(auth.getName()).stream()
                .map(this::applicationSummary).toList();
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body, Authentication auth) {
        ExportApplication application = new ExportApplication();
        application.setFarmerId(auth.getName());
        applyApplicationFields(application, body, true);
        ExportApplication saved = applications.save(application);

        List<ExportTask> initialTasks = new ArrayList<>();
        for (StageTask task : STAGE_TASKS) {
            initialTasks.add(new ExportTask(saved.getId(), null, task.key(), task.stage(), task.title()));
        }
        requirements.findByProductIgnoreCaseAndDestinationIgnoreCaseOrderByIdAsc(
                normalizeProduct(saved.getProduct()), saved.getDestinationCountry()).forEach(requirement ->
                initialTasks.add(new ExportTask(saved.getId(), requirement.getId(),
                        "requirement-" + requirement.getId(), 2, requirement.getName())));
        tasks.saveAll(initialTasks);
        return details(saved);
    }

    @GetMapping("/applications/{id}")
    public Map<String, Object> get(@PathVariable Long id, Authentication auth) {
        return details(ownedApplication(id, auth));
    }

    @PutMapping("/applications/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     Authentication auth) {
        ExportApplication application = editableApplication(id, auth);
        applyApplicationFields(application, body, false);
        return details(applications.save(application));
    }

    @PutMapping("/applications/{id}/tasks/{taskKey}")
    @Transactional
    public Map<String, Object> updateTask(@PathVariable Long id, @PathVariable String taskKey,
                                          @RequestBody Map<String, Object> body, Authentication auth) {
        editableApplication(id, auth);
        ExportTask task = tasks.findByApplicationIdAndTaskKey(id, taskKey)
                .orElseThrow(() -> notFound("Checklist item not found"));
        String status = string(body, "status");
        if (!TASK_STATUSES.contains(status)) throw bad("Choose a valid checklist status");
        task.setStatus(status);
        task.setStatusRecorded(true);
        task.setNotes(optionalString(body, "notes", 2000));
        tasks.save(task);
        return details(ownedApplication(id, auth));
    }

    @GetMapping("/requirements")
    public Map<String, Object> requirements(@RequestParam String product, @RequestParam String destination) {
        List<Map<String, Object>> found = requirements.findByProductIgnoreCaseAndDestinationIgnoreCaseOrderByIdAsc(
                        normalizeProduct(product), destination.trim()).stream().map(this::requirementView).toList();
        return Map.of("items", found, "supported", !found.isEmpty(),
                "message", found.isEmpty()
                        ? "No product-and-destination requirement records are in this starter catalogue. Confirm current requirements with the responsible authorities."
                        : "This starter checklist is illustrative and unverified for the selected product and destination. Confirm all applicable requirements with the responsible authorities.");
    }

    @PostMapping(value = "/applications/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> uploadDocument(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                              @RequestParam String documentType,
                                              @RequestParam(required = false) Integer stageNumber,
                                              @RequestParam(required = false) Long requirementId,
                                              @RequestParam(required = false) String expiryDate,
                                              @RequestParam(required = false) String notes,
                                              Authentication auth) throws IOException {
        editableApplication(id, auth);
        if (file.isEmpty()) throw bad("Choose a document to upload");
        if (file.getSize() > MAX_FILE_BYTES) throw new ResponseStatusException(
                HttpStatus.valueOf(413), "Document is too large. The maximum size is 10 MB.");
        if (!DOCUMENT_TYPES.contains(documentType)) throw bad("Choose a valid document category");
        if (stageNumber != null && (stageNumber < 1 || stageNumber > 8)) throw bad("Stage must be between 1 and 8");
        if (requirementId != null && tasks.findByApplicationIdAndTaskKey(id, "requirement-" + requirementId).isEmpty()) {
            throw bad("That requirement is not linked to this application");
        }
        LocalDate parsedExpiry = parseDate(expiryDate, "expiry date");
        byte[] bytes = file.getBytes();
        String contentType = detectedType(file.getOriginalFilename(), bytes);
        String fileName = safeFileName(file.getOriginalFilename());

        ExportDocument document = new ExportDocument();
        document.setApplicationId(id);
        document.setStageNumber(stageNumber);
        document.setRequirementId(requirementId);
        document.setDocumentType(documentType);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setExpiryDate(parsedExpiry);
        document.setNotes(clean(notes, 1000));
        document.setData(bytes);
        return documentView(documents.save(document));
    }

    @PutMapping("/documents/{documentId}")
    @Transactional
    public Map<String, Object> updateDocument(@PathVariable Long documentId, @RequestBody Map<String, Object> body,
                                              Authentication auth) {
        ExportDocument document = ownedDocument(documentId, auth);
        editableApplication(document.getApplicationId(), auth);
        String status = optionalString(body, "recordStatus", 40);
        if (status != null) {
            if (!DOCUMENT_STATUSES.contains(status)) throw bad("Choose a valid internal recordkeeping status");
            document.setRecordStatus(status);
        }
        if (body.containsKey("notes")) document.setNotes(optionalString(body, "notes", 1000));
        if (body.containsKey("expiryDate")) document.setExpiryDate(parseDate(optionalString(body, "expiryDate", 40), "expiry date"));
        return documentView(documents.save(document));
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long documentId, Authentication auth) {
        ExportDocument document = ownedDocument(documentId, auth);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.getData());
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteDocument(@PathVariable Long documentId, Authentication auth) {
        ExportDocument document = ownedDocument(documentId, auth);
        editableApplication(document.getApplicationId(), auth);
        if ("OFFICIAL_CERTIFICATE".equals(document.getDocumentType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Uploaded official certificate records are retained and cannot be deleted from a consignment file.");
        }
        documents.delete(document);
    }

    @PostMapping("/applications/{id}/consignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createConsignment(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                                 Authentication auth) {
        editableApplication(id, auth);
        Map<String, String> details = consignmentDetails(body);
        validateCertificateRecord(id, details);
        try {
            ExportConsignment saved = consignments.save(new ExportConsignment(id, objectMapper.writeValueAsString(details)));
            return consignmentView(saved);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not save consignment details", e);
        }
    }

    @PutMapping("/applications/{id}/consignments/{consignmentId}")
    @Transactional
    public Map<String, Object> updateConsignment(@PathVariable Long id, @PathVariable Long consignmentId,
                                                 @RequestBody Map<String, Object> body, Authentication auth) {
        editableApplication(id, auth);
        ExportConsignment consignment = consignments.findById(consignmentId)
                .filter(item -> item.getApplicationId().equals(id))
                .orElseThrow(() -> notFound("Consignment record not found"));
        Map<String, String> oldDetails = readConsignment(consignment.getDetailsJson());
        Map<String, String> updated = consignmentDetails(body);
        boolean certificateWasIssued = Boolean.parseBoolean(oldDetails.getOrDefault("certificateIssued", "false"));
        for (String certificateField : CERTIFICATE_FIELDS) {
            String prior = oldDetails.get(certificateField);
            if (certificateWasIssued && prior != null && !prior.isBlank()
                    && !prior.equals(updated.getOrDefault(certificateField, ""))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Certificate details already recorded for this consignment cannot be edited. Add a note or contact the issuing authority about corrections.");
            }
            if (!updated.containsKey(certificateField) && prior != null) updated.put(certificateField, prior);
        }
        validateCertificateRecord(id, updated);
        try {
            consignment.setDetailsJson(objectMapper.writeValueAsString(updated));
            return consignmentView(consignments.save(consignment));
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not save consignment details", e);
        }
    }

    @PutMapping("/applications/{id}/close")
    @Transactional
    public Map<String, Object> close(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                     Authentication auth) {
        ExportApplication application = editableApplication(id, auth);
        String reference = requiredString(body, "shipmentReference", 200);
        String outcome = requiredString(body, "shipmentOutcome", 50);
        if (!Set.of("SHIPPED", "PARTIALLY_SHIPPED", "CANCELLED", "REJECTED", "OTHER").contains(outcome)) {
            throw bad("Choose an actual shipment outcome");
        }
        LocalDate date = parseDate(requiredString(body, "shipmentDate", 40), "shipment date");
        if ((outcome.equals("SHIPPED") || outcome.equals("PARTIALLY_SHIPPED"))
                && documents.countByApplicationIdAndDocumentType(id, "CUSTOMS_CLEARANCE") == 0) {
            throw bad("Actual shipment closure needs customs-clearance evidence. Upload it under Stage 7 Customs clearance.");
        }
        application.setShipmentReference(reference);
        application.setShipmentOutcome(outcome);
        application.setShipmentDate(date);
        application.setUnresolvedIssues(optionalString(body, "unresolvedIssues", 2000));
        application.setLessonsLearned(optionalString(body, "lessonsLearned", 2000));
        application.setClosed(true);
        application.setClosedAt(LocalDateTime.now());
        application.setStatus("CLOSED");
        return details(applications.save(application));
    }

    private void applyApplicationFields(ExportApplication application, Map<String, Object> body, boolean creating) {
        String product = body.containsKey("product") ? requiredString(body, "product", 120) : application.getProduct();
        String destination = body.containsKey("destinationCountry")
                ? requiredString(body, "destinationCountry", 100) : application.getDestinationCountry();
        String location = body.containsKey("productionLocation")
                ? requiredString(body, "productionLocation", 200) : application.getProductionLocation();
        BigDecimal quantity = body.containsKey("estimatedQuantity")
                ? decimal(body.get("estimatedQuantity")) : application.getEstimatedQuantity();
        String unit = body.containsKey("quantityUnit")
                ? requiredString(body, "quantityUnit", 40) : application.getQuantityUnit();
        if (creating || body.containsKey("product")) application.setProduct(product);
        if (creating || body.containsKey("destinationCountry")) application.setDestinationCountry(destination);
        if (creating || body.containsKey("productionLocation")) application.setProductionLocation(location);
        if (creating || body.containsKey("estimatedQuantity")) {
            if (quantity == null || quantity.signum() <= 0) throw bad("Estimated quantity must be greater than zero");
            application.setEstimatedQuantity(quantity);
        }
        if (creating || body.containsKey("quantityUnit")) application.setQuantityUnit(unit);
        if (body.containsKey("variety")) application.setVariety(optionalString(body, "variety", 120));
        if (body.containsKey("province")) application.setProvince(optionalString(body, "province", 80));
        if (body.containsKey("buyerDetails")) application.setBuyerDetails(optionalString(body, "buyerDetails", 1000));
        applyRegistration(body, "businessRegistrationStatus", application::setBusinessRegistrationStatus);
        applyRegistration(body, "taxRegistrationStatus", application::setTaxRegistrationStatus);
        applyRegistration(body, "exporterRegistrationStatus", application::setExporterRegistrationStatus);
    }

    private void applyRegistration(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        if (!body.containsKey(key)) return;
        String status = string(body, key);
        if (!REGISTRATION_STATUSES.contains(status)) throw bad("Choose a valid registration tracking status for " + key);
        setter.accept(status);
    }

    private Map<String, Object> details(ExportApplication application) {
        List<ExportTask> applicationTasks = tasks.findByApplicationIdOrderByStageNumberAscIdAsc(application.getId());
        Map<String, Object> progress = checklistProgress(applicationTasks);
        List<Map<String, Object>> requirementViews = requirements.findByProductIgnoreCaseAndDestinationIgnoreCaseOrderByIdAsc(
                        normalizeProduct(application.getProduct()), application.getDestinationCountry()).stream()
                .map(this::requirementView).toList();
        Map<String, Object> result = new LinkedHashMap<>(applicationSummary(application));
        result.put("tasks", applicationTasks.stream().map(this::taskView).toList());
        result.put("requirements", requirementViews);
        result.put("documents", documents.findByApplicationIdOrderByUploadedAtDesc(application.getId()).stream()
                .map(this::documentView).toList());
        result.put("consignments", consignments.findByApplicationIdOrderByCreatedAtDesc(application.getId()).stream()
                .map(this::consignmentView).toList());
        result.putAll(progress);
        return result;
    }

    private Map<String, Object> applicationSummary(ExportApplication app) {
        List<ExportTask> applicationTasks = tasks.findByApplicationIdOrderByStageNumberAscIdAsc(app.getId());
        Map<String, Object> progress = checklistProgress(applicationTasks);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", app.getId());
        result.put("product", app.getProduct());
        result.put("variety", app.getVariety());
        result.put("destinationCountry", app.getDestinationCountry());
        result.put("productionLocation", app.getProductionLocation());
        result.put("province", app.getProvince());
        result.put("estimatedQuantity", app.getEstimatedQuantity());
        result.put("quantityUnit", app.getQuantityUnit());
        result.put("buyerDetails", app.getBuyerDetails());
        result.put("businessRegistrationStatus", app.getBusinessRegistrationStatus());
        result.put("taxRegistrationStatus", app.getTaxRegistrationStatus());
        result.put("exporterRegistrationStatus", app.getExporterRegistrationStatus());
        result.put("status", app.getStatus());
        result.put("createdAt", app.getCreatedAt());
        result.put("updatedAt", app.getUpdatedAt());
        result.put("shipmentReference", app.getShipmentReference());
        result.put("shipmentOutcome", app.getShipmentOutcome());
        result.put("shipmentDate", app.getShipmentDate());
        result.put("unresolvedIssues", app.getUnresolvedIssues());
        result.put("lessonsLearned", app.getLessonsLearned());
        result.put("closed", app.isClosed());
        result.put("closedAt", app.getClosedAt());
        result.putAll(progress);
        return result;
    }

    private Map<String, Object> checklistProgress(List<ExportTask> applicationTasks) {
        long completed = applicationTasks.stream().filter(task -> task.getStatus().equals("COMPLETED")).count();
        long pending = applicationTasks.stream().filter(task -> Set.of(
                "NOT_STARTED", "IN_PROGRESS", "AWAITING_DOCUMENTS", "SUBMITTED").contains(task.getStatus())).count();
        long actionRequired = applicationTasks.stream().filter(task -> task.getStatus().equals("ACTION_REQUIRED")).count();
        long evidenceChecked = applicationTasks.stream().filter(task -> task.getStatus().equals("EVIDENCE_CHECKED")).count();
        long notApplicable = applicationTasks.stream().filter(task -> task.getStatus().equals("NOT_APPLICABLE")).count();
        long denominator = applicationTasks.size() - notApplicable;
        int progress = denominator == 0 ? 0 : (int) Math.round(100.0 * completed / denominator);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("progress", progress);
        result.put("checklistCounts", Map.of(
                "total", applicationTasks.size(),
                "completed", completed,
                "pending", pending,
                "actionRequired", actionRequired,
                "evidenceChecked", evidenceChecked,
                "notApplicable", notApplicable));
        result.put("allChecklistItemsRecorded", !applicationTasks.isEmpty()
                && applicationTasks.stream().allMatch(ExportTask::isStatusRecorded));
        result.put("progressDescription", "Checklist completion does not mean official requirements have been satisfied.");
        return result;
    }

    private Map<String, Object> taskView(ExportTask task) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", task.getId());
        value.put("taskKey", task.getTaskKey());
        value.put("requirementId", task.getRequirementId());
        value.put("stageNumber", task.getStageNumber());
        value.put("title", task.getTitle());
        value.put("status", task.getStatus());
        value.put("statusRecorded", task.isStatusRecorded());
        value.put("notes", task.getNotes());
        return value;
    }

    private Map<String, Object> requirementView(ExportRequirement requirement) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", requirement.getId());
        value.put("product", requirement.getProduct());
        value.put("destination", requirement.getDestination());
        value.put("name", requirement.getName());
        value.put("explanation", requirement.getExplanation());
        value.put("hsCode", requirement.getHsCode());
        value.put("authority", requirement.getAuthority());
        value.put("applicability", requirement.getApplicability());
        value.put("documentsNeeded", requirement.getDocumentsNeeded());
        value.put("sourceUrl", requirement.getSourceUrl());
        value.put("lastVerifiedDate", requirement.getLastVerifiedDate());
        value.put("verificationStatus", requirement.getVerificationStatus());
        value.put("followUp", requirement.getFollowUp());
        value.put("leadTime", requirement.getLeadTime());
        value.put("cost", requirement.getCost());
        return value;
    }

    private Map<String, Object> documentView(ExportDocument document) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", document.getId());
        value.put("applicationId", document.getApplicationId());
        value.put("stageNumber", document.getStageNumber());
        value.put("requirementId", document.getRequirementId());
        value.put("documentType", document.getDocumentType());
        value.put("fileName", document.getFileName());
        value.put("contentType", document.getContentType());
        value.put("expiryDate", document.getExpiryDate());
        value.put("notes", document.getNotes());
        value.put("recordStatus", document.getRecordStatus());
        value.put("uploadedAt", document.getUploadedAt());
        value.put("downloadUrl", "/api/exports/documents/" + document.getId() + "/download");
        return value;
    }

    private Map<String, Object> consignmentView(ExportConsignment consignment) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", consignment.getId());
        value.put("applicationId", consignment.getApplicationId());
        value.put("details", readConsignment(consignment.getDetailsJson()));
        value.put("createdAt", consignment.getCreatedAt());
        value.put("updatedAt", consignment.getUpdatedAt());
        value.put("certificateNotice", "Certificate details are farmer-recorded information, not independently verified or issued by AgriTech.");
        return value;
    }

    private Map<String, String> consignmentDetails(Map<String, Object> body) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : body.keySet()) {
            if (!CONSIGNMENT_FIELDS.contains(key)) throw bad("Unrecognised consignment field: " + key);
            String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
            if (value.length() > CONSIGNMENT_FIELD_LIMITS.get(key)) throw bad("Consignment field is too long: " + key);
            if (!value.isBlank()) result.put(key, value);
        }
        if (result.isEmpty()) throw bad("Enter at least one consignment detail");
        return result;
    }

    private void validateCertificateRecord(Long applicationId, Map<String, String> details) {
        boolean hasCertificateDetails = CERTIFICATE_FIELDS.stream()
                .filter(key -> !"certificateIssued".equals(key))
                .anyMatch(key -> details.containsKey(key) && !details.get(key).isBlank());
        boolean issued = Boolean.parseBoolean(details.getOrDefault("certificateIssued", "false"));
        if (hasCertificateDetails && !issued) throw bad(
                "Only record certificate details after an official certificate has actually been issued.");
        if (issued) {
            if (blank(details.get("certificateNumber")) || blank(details.get("certificateType"))
                    || blank(details.get("certificateIssueDate"))) {
                throw bad("For an issued certificate, enter its actual type, number and issue date.");
            }
            if (documents.countByApplicationIdAndDocumentType(applicationId, "OFFICIAL_CERTIFICATE") == 0) {
                throw bad("Upload the certificate actually received before recording its issue details. The upload is not automatically verified.");
            }
            parseDate(details.get("certificateIssueDate"), "certificate issue date");
        }
    }

    private Map<String, String> readConsignment(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JacksonException e) {
            throw new IllegalStateException("Saved consignment details could not be read", e);
        }
    }

    private ExportApplication ownedApplication(Long id, Authentication auth) {
        ExportApplication app = applications.findById(id)
                .orElseThrow(() -> notFound("Export application not found"));
        if (!app.getFarmerId().equals(auth.getName())) throw notFound("Export application not found");
        return app;
    }

    private ExportApplication editableApplication(Long id, Authentication auth) {
        ExportApplication app = ownedApplication(id, auth);
        if (app.isClosed()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This export record is closed and can no longer be edited.");
        return app;
    }

    private ExportDocument ownedDocument(Long id, Authentication auth) {
        ExportDocument document = documents.findById(id).orElseThrow(() -> notFound("Document not found"));
        ownedApplication(document.getApplicationId(), auth);
        return document;
    }

    private static String detectedType(String originalName, byte[] bytes) {
        if (bytes.length > 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length > 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return "image/png";
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        if (bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') return "application/pdf";
        if (originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".csv") && isUtf8Text(bytes)) return "text/csv";
        throw bad("Only PDF, JPEG, PNG, WebP or CSV documents are allowed");
    }

    private static boolean isUtf8Text(byte[] bytes) {
        if (bytes.length == 0) return false;
        for (byte value : bytes) if (value == 0) return false;
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static String safeFileName(String name) {
        if (name == null || name.isBlank()) throw bad("File name is missing");
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "_").trim();
        if (base.isBlank() || base.length() > 255) throw bad("File name is invalid or too long");
        return base;
    }

    private static LocalDate parseDate(String value, String label) {
        if (blank(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException e) {
            throw bad("Enter a valid " + label);
        }
    }

    private static BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (RuntimeException e) {
            throw bad("Enter a valid estimated quantity");
        }
    }

    private static String requiredString(Map<String, Object> body, String key, int limit) {
        String value = string(body, key);
        if (value.isBlank()) throw bad("Enter " + key);
        if (value.length() > limit) throw bad(key + " is too long");
        return value;
    }

    private static String optionalString(Map<String, Object> body, String key, int limit) {
        if (!body.containsKey(key) || body.get(key) == null) return null;
        return clean(String.valueOf(body.get(key)), limit);
    }

    private static String clean(String value, int limit) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.length() > limit) throw bad("A text field is too long");
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String normalizeProduct(String product) {
        String value = product.trim();
        return value.equalsIgnoreCase("avocados") ? "avocado" : value;
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    private record StageTask(String key, int stage, String title) { }
}
