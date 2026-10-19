package tut.ac.za.AgriFinanceAPIs.export;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/export")
public class ExportShipmentController {

    private final ExportShipmentRepository repository;

    public ExportShipmentController(ExportShipmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{farmerId}")
    public Map<String, Object> getFarmerExports(@PathVariable String farmerId) {
        List<ExportShipment> shipments = repository.findByFarmerId(farmerId);
        List<Map<String, String>> authorityGuide = List.of(
                Map.of("authority", "PPECB", "requirement", "Exporter registration, cold-chain protocols, and export inspection booking", "link", "https://ppecb.com"),
                Map.of("authority", "Department of Agriculture", "requirement", "Production unit registration (PUC), packhouse code (PHC), and phytosanitary certificate", "link", "https://www.dalrrd.gov.za"),
                Map.of("authority", "SARS Customs", "requirement", "Customs exporter registration, HS tariff classification, and export declaration", "link", "https://www.sars.gov.za/customs-and-excise"),
                Map.of("authority", "Destination Country", "requirement", "Import permits, Maximum Residue Limits (MRLs), and labelling rules", "link", "https://www.macmap.org")
        );
        return Map.of("shipments", shipments, "requirementsByAuthority", authorityGuide);
    }

    @PostMapping("/{farmerId}")
    public ExportShipment createShipment(@PathVariable String farmerId, @RequestBody ExportShipment shipment) {
        shipment.setFarmerId(farmerId);
        return repository.save(shipment);
    }
}
