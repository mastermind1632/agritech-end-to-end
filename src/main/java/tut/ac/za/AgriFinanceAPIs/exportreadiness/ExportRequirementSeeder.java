package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExportRequirementSeeder implements CommandLineRunner {

    private final ExportRequirementRepository requirements;

    public ExportRequirementSeeder(ExportRequirementRepository requirements) {
        this.requirements = requirements;
    }

    @Override
    public void run(String... args) {
        if (requirements.count() > 0) return;
        requirements.saveAll(List.of(
                new ExportRequirement(
                        "avocado-netherlands-market-access", "avocado", "Netherlands",
                        "Confirm market access for South African avocados",
                        "This is a verification prompt, not a statement that this trade is currently permitted. Ask the responsible plant-health authority and the destination authority for the current product-specific conditions.",
                        null, "South African plant-health authority and destination-country authorities",
                        "TO_CONFIRM", "Written market-access response and any applicable protocol",
                        "https://www.ippc.int/en/countries/south-africa/", "UNVERIFIED",
                        "Confirm whether market access exists for this exact origin, product and destination before making export plans."),
                new ExportRequirement(
                        "avocado-netherlands-plant-health", "avocado", "Netherlands",
                        "Ask whether plant-health certification, inspection or treatment applies",
                        "The applicable plant-health steps depend on current official conditions for the product and destination. Confirm them with the responsible authority; none are treated as confirmed here.",
                        null, "South African plant-health authority and destination-country authorities",
                        "TO_CONFIRM", "Authority instructions and any certificate or inspection evidence actually issued",
                        "https://www.gov.za/services/services-organisations/export-permits/ecertification-ecert", "UNVERIFIED",
                        "Ask the authority which certificate, inspection, treatment or other evidence applies, if any."),
                new ExportRequirement(
                        "avocado-netherlands-import-conditions", "avocado", "Netherlands",
                        "Check Netherlands and EU import conditions",
                        "Use the official destination-market source to check the current import conditions for this product and origin. This record does not confirm a permit or approval.",
                        null, "Destination-country authorities",
                        "TO_CONFIRM", "Dated search result or written response from the responsible destination authority",
                        "https://trade.ec.europa.eu/access-to-markets/", "UNVERIFIED",
                        "Confirm applicable permits, border checks and plant-health conditions with the competent destination authority."),
                new ExportRequirement(
                        "avocado-netherlands-residue-limits", "avocado", "Netherlands",
                        "Confirm pesticide residue limits for the destination market",
                        "Ask the responsible authority and buyer which current residue limits and evidence apply. No limits or treatments are specified by this checklist.",
                        null, "Destination-country authorities and buyer",
                        "TO_CONFIRM", "Current official limit source and any buyer specification",
                        "https://trade.ec.europa.eu/access-to-markets/", "UNVERIFIED",
                        "Record the date and source of the limits checked; confirm buyer requirements separately."),
                new ExportRequirement(
                        "avocado-netherlands-customs-classification", "avocado", "Netherlands",
                        "Ask SARS for tariff classification guidance",
                        "Record the HS or tariff classification only after it has been confirmed with the appropriate customs source. This checklist does not assign a code.",
                        null, "SARS Customs",
                        "TO_CONFIRM", "Written classification guidance or the source used to confirm the code",
                        "https://www.sars.gov.za/customs-and-excise/", "UNVERIFIED",
                        "Do not use an unconfirmed HS code for declarations or tariff calculations.")
        ));
    }
}
