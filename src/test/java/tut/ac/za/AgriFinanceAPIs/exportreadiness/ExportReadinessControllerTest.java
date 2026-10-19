package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportReadinessControllerTest {

    private ExportApplicationRepository applications;
    private ExportTaskRepository tasks;
    private ExportRequirementRepository requirements;
    private ExportDocumentRepository documents;
    private ExportConsignmentRepository consignments;
    private ExportReadinessController controller;
    private final TestingAuthenticationToken farmer = new TestingAuthenticationToken("farmer-1", "ignored");

    @BeforeEach
    void setUp() {
        applications = mock(ExportApplicationRepository.class);
        tasks = mock(ExportTaskRepository.class);
        requirements = mock(ExportRequirementRepository.class);
        documents = mock(ExportDocumentRepository.class);
        consignments = mock(ExportConsignmentRepository.class);
        controller = new ExportReadinessController(
                applications, tasks, requirements, documents, consignments, null);
    }

    @Test
    void createPersistsAnApplicationAndEightStageChecklist() {
        when(applications.save(any(ExportApplication.class))).thenAnswer(invocation -> {
            ExportApplication saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });
        when(tasks.saveAll(any())).thenAnswer(invocation -> {
            Iterable<ExportTask> submitted = invocation.getArgument(0);
            List<ExportTask> saved = new ArrayList<>();
            submitted.forEach(saved::add);
            when(tasks.findByApplicationIdOrderByStageNumberAscIdAsc(12L)).thenReturn(saved);
            return saved;
        });

        Map<String, Object> created = controller.create(Map.of(
                "product", "Avocado",
                "destinationCountry", "Netherlands",
                "productionLocation", "Example farm",
                "estimatedQuantity", 100,
                "quantityUnit", "kg"), farmer);

        assertEquals(12L, created.get("id"));
        assertEquals("IN_PROGRESS", created.get("status"));
        assertEquals("NOT_ASSESSED", created.get("businessRegistrationStatus"));
        assertEquals(17, ((List<?>) created.get("tasks")).size());
        assertEquals(0, created.get("progress"));
    }

    @Test
    void progressDistinguishesCompletedAndSavedChecklistStatus() {
        ExportApplication app = ownedApplication();
        ExportTask task = new ExportTask(7L, null, "eligibility-product", 1, "Record product details");
        when(applications.findById(7L)).thenReturn(Optional.of(app));
        when(tasks.findByApplicationIdAndTaskKey(7L, "eligibility-product")).thenReturn(Optional.of(task));
        when(tasks.findByApplicationIdOrderByStageNumberAscIdAsc(7L)).thenReturn(List.of(task));

        Map<String, Object> updated = controller.updateTask(7L, "eligibility-product",
                Map.of("status", "COMPLETED", "notes", "Recorded by farmer"), farmer);

        assertEquals("COMPLETED", task.getStatus());
        assertTrue(task.isStatusRecorded());
        assertEquals(100, updated.get("progress"));
        assertTrue(Boolean.TRUE.equals(updated.get("allChecklistItemsRecorded")));
        assertEquals(1L, ((Map<?, ?>) updated.get("checklistCounts")).get("completed"));
        assertTrue(String.valueOf(updated.get("progressDescription")).contains("official requirements"));
    }

    @Test
    void actualShippedClosureRequiresCustomsEvidence() {
        ExportApplication app = ownedApplication();
        when(applications.findById(7L)).thenReturn(Optional.of(app));
        when(tasks.findByApplicationIdOrderByStageNumberAscIdAsc(7L)).thenReturn(List.of());

        Map<String, Object> closure = Map.of(
                "shipmentReference", "REF-123",
                "shipmentOutcome", "SHIPPED",
                "shipmentDate", "2026-10-16");

        when(documents.countByApplicationIdAndDocumentType(7L, "CUSTOMS_CLEARANCE")).thenReturn(0L);
        ResponseStatusException missingEvidence = assertThrows(ResponseStatusException.class,
                () -> controller.close(7L, closure, farmer));
        assertEquals(HttpStatus.BAD_REQUEST, missingEvidence.getStatusCode());
        assertFalse(app.isClosed());

        when(documents.countByApplicationIdAndDocumentType(7L, "CUSTOMS_CLEARANCE")).thenReturn(1L);
        when(applications.save(app)).thenReturn(app);
        Map<String, Object> result = controller.close(7L, closure, farmer);

        assertTrue(app.isClosed());
        assertEquals("CLOSED", result.get("status"));
        assertEquals("SHIPPED", result.get("shipmentOutcome"));
        assertFalse(result.containsKey("customsCleared"));
    }

    @Test
    void uploadedCustomsDocumentIsPersistedWithStageAndType() throws Exception {
        when(applications.findById(7L)).thenReturn(Optional.of(ownedApplication()));
        when(documents.save(any(ExportDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = controller.uploadDocument(7L,
                new MockMultipartFile("file", "clearance.csv", "text/csv", "a,b\n".getBytes()),
                "CUSTOMS_CLEARANCE", 7, null, null, null, farmer);

        assertEquals("clearance.csv", result.get("fileName"));
        assertEquals(7, result.get("stageNumber"));
        assertEquals("CUSTOMS_CLEARANCE", result.get("documentType"));
        org.mockito.ArgumentCaptor<ExportDocument> saved = org.mockito.ArgumentCaptor.forClass(ExportDocument.class);
        verify(documents).save(saved.capture());
        assertEquals("CUSTOMS_CLEARANCE", saved.getValue().getDocumentType());
    }

    @Test
    void progressKeepsPendingActionRequiredAndNotApplicableDistinct() {
        ExportApplication app = ownedApplication();
        List<ExportTask> items = List.of(
                new ExportTask(7L, null, "done", 1, "Done"),
                new ExportTask(7L, null, "pending", 1, "Pending"),
                new ExportTask(7L, null, "action", 2, "Action"),
                new ExportTask(7L, null, "na", 3, "Not applicable"));
        items.get(0).setStatus("COMPLETED");
        items.get(1).setStatus("IN_PROGRESS");
        items.get(2).setStatus("ACTION_REQUIRED");
        items.get(3).setStatus("NOT_APPLICABLE");
        when(applications.findById(7L)).thenReturn(Optional.of(app));
        when(tasks.findByApplicationIdAndTaskKey(7L, "pending")).thenReturn(Optional.of(items.get(1)));
        when(tasks.findByApplicationIdOrderByStageNumberAscIdAsc(7L)).thenReturn(items);

        Map<String, Object> result = controller.updateTask(7L, "pending", Map.of(
                "status", "IN_PROGRESS"), farmer);

        Map<?, ?> counts = (Map<?, ?>) result.get("checklistCounts");
        assertEquals(1L, counts.get("completed"));
        assertEquals(1L, counts.get("pending"));
        assertEquals(1L, counts.get("actionRequired"));
        assertEquals(1L, counts.get("notApplicable"));
        assertEquals(33, result.get("progress"));
    }

    private ExportApplication ownedApplication() {
        ExportApplication app = new ExportApplication();
        ReflectionTestUtils.setField(app, "id", 7L);
        app.setFarmerId("farmer-1");
        app.setProduct("Avocado");
        app.setDestinationCountry("Netherlands");
        return app;
    }
}
