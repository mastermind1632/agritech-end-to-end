package tut.ac.za.AgriFinanceAPIs;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.compliance.BusinessCompliance;
import tut.ac.za.AgriFinanceAPIs.compliance.BusinessComplianceController;
import tut.ac.za.AgriFinanceAPIs.compliance.BusinessComplianceRepository;
import tut.ac.za.AgriFinanceAPIs.compliance.ComplianceReminderController;
import tut.ac.za.AgriFinanceAPIs.compliance.ComplianceReminderRepository;
import tut.ac.za.AgriFinanceAPIs.compliance.ComplianceReminderService;
import tut.ac.za.AgriFinanceAPIs.compliance.ReminderDeliveryController;
import tut.ac.za.AgriFinanceAPIs.compliance.delivery.ReminderDeliveryService;
import tut.ac.za.AgriFinanceAPIs.export.ExportShipment;
import tut.ac.za.AgriFinanceAPIs.export.ExportShipmentController;
import tut.ac.za.AgriFinanceAPIs.export.ExportShipmentRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationsOwnershipTest {
    private final Authentication farmer = new TestingAuthenticationToken("farmer-1", "ignored");

    @Test
    void exportEndpointsRejectOtherFarmersAndBindCreatedShipmentToOwner() {
        ExportShipmentRepository repository = mock(ExportShipmentRepository.class);
        ExportShipmentController controller = new ExportShipmentController(repository);

        ResponseStatusException readFailure = assertThrows(ResponseStatusException.class,
                () -> controller.getFarmerExports("farmer-2", farmer));
        assertEquals(HttpStatus.FORBIDDEN, readFailure.getStatusCode());

        ResponseStatusException writeFailure = assertThrows(ResponseStatusException.class,
                () -> controller.createShipment("farmer-2", new ExportShipment(), farmer));
        assertEquals(HttpStatus.FORBIDDEN, writeFailure.getStatusCode());
        verifyNoInteractions(repository);

        ExportShipment shipment = new ExportShipment();
        shipment.setFarmerId("farmer-2");
        when(repository.save(any(ExportShipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.createShipment("farmer-1", shipment, farmer);

        assertEquals("farmer-1", shipment.getFarmerId());
        verify(repository).save(shipment);
    }

    @Test
    void complianceEndpointsRejectOtherFarmersBeforeReadingOrWriting() {
        BusinessComplianceRepository repository = mock(BusinessComplianceRepository.class);
        ComplianceReminderService reminderService = mock(ComplianceReminderService.class);
        BusinessComplianceController controller = new BusinessComplianceController(repository, reminderService);

        ResponseStatusException readFailure = assertThrows(ResponseStatusException.class,
                () -> controller.getComplianceChecklist("farmer-2", farmer));
        assertEquals(HttpStatus.FORBIDDEN, readFailure.getStatusCode());

        ResponseStatusException writeFailure = assertThrows(ResponseStatusException.class,
                () -> controller.updateCompliance("farmer-2", new BusinessCompliance(), farmer));
        assertEquals(HttpStatus.FORBIDDEN, writeFailure.getStatusCode());
        verifyNoInteractions(repository, reminderService);
    }

    @Test
    void reminderEndpointsRejectOtherFarmersBeforeAccessingReminderServices() {
        ComplianceReminderService reminderService = mock(ComplianceReminderService.class);
        ComplianceReminderController remindersController = new ComplianceReminderController(reminderService);
        ComplianceReminderRepository repository = mock(ComplianceReminderRepository.class);
        ReminderDeliveryService deliveryService = mock(ReminderDeliveryService.class);
        ReminderDeliveryController deliveryController = new ReminderDeliveryController(repository, deliveryService);

        ResponseStatusException listFailure = assertThrows(ResponseStatusException.class,
                () -> remindersController.list("farmer-2", farmer));
        assertEquals(HttpStatus.FORBIDDEN, listFailure.getStatusCode());

        ResponseStatusException regenerateFailure = assertThrows(ResponseStatusException.class,
                () -> remindersController.regenerate("farmer-2", farmer));
        assertEquals(HttpStatus.FORBIDDEN, regenerateFailure.getStatusCode());

        ResponseStatusException deliveryFailure = assertThrows(ResponseStatusException.class,
                () -> deliveryController.sendNow("farmer-2", 12L, farmer));
        assertEquals(HttpStatus.FORBIDDEN, deliveryFailure.getStatusCode());
        verifyNoInteractions(reminderService, repository, deliveryService);
    }
}
