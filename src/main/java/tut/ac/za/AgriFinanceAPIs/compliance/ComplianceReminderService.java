package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ComplianceReminderService {

    private static final int[] LEAD_DAYS = {30, 7, 1};

    private final ComplianceReminderRepository reminders;
    private final BusinessComplianceRepository complianceRepository;
    private final FarmerRepository farmers;

    public ComplianceReminderService(ComplianceReminderRepository reminders,
                                     BusinessComplianceRepository complianceRepository,
                                     FarmerRepository farmers) {
        this.reminders = reminders;
        this.complianceRepository = complianceRepository;
        this.farmers = farmers;
    }

    public List<ComplianceReminder> listForFarmer(String farmerId) {
        if (!farmers.existsById(farmerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found: " + farmerId);
        }
        return reminders.findByFarmerIdOrderByDueDateAsc(farmerId);
    }

    @Transactional
    public List<ComplianceReminder> regenerateForFarmer(String farmerId) {
        if (!farmers.existsById(farmerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found: " + farmerId);
        }
        BusinessCompliance c = complianceRepository.findByFarmerId(farmerId).orElse(null);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "No business compliance profile exists for farmer " + farmerId
                    + ". Create one first via POST /api/compliance/{farmerId}.");
        }

        reminders.deleteByFarmerId(farmerId);

        LocalDate today = LocalDate.now();
        List<ComplianceReminder> generated = new ArrayList<>();

        addDeadline(generated, farmerId, "CIPC_ANNUAL_RETURN", today.plusYears(1), today);
        addDeadline(generated, farmerId, "SARS_INCOME_TAX", nextOccurrence(10, 31, today), today);
        addDeadline(generated, farmerId, "SARS_PROVISIONAL_TAX", nextOccurrence(8, 31, today), today);

        if (c.isVatRegistered()) {
            addDeadline(generated, farmerId, "VAT_RETURN", nextOccurrenceOf25th(today), today);
        }
        if (c.isHasEmployees()) {
            addDeadline(generated, farmerId, "UIF_MONTHLY", nextOccurrence(1, 7, today), today);
            addDeadline(generated, farmerId, "COIDA_ANNUAL", nextOccurrence(3, 31, today), today);
        }

        return reminders.saveAll(generated);
    }

    private void addDeadline(List<ComplianceReminder> out, String farmerId,
                             String type, LocalDate dueDate, LocalDate today) {
        for (int lead : LEAD_DAYS) {
            LocalDate trigger = dueDate.minusDays(lead);
            if (trigger.isBefore(today)) continue;
            ComplianceReminder r = new ComplianceReminder();
            r.setFarmerId(farmerId);
            r.setReminderType(type);
            r.setDueDate(dueDate);
            r.setDaysBefore(lead);
            r.setTriggerDate(trigger);
            r.setChannel("IN_APP");
            r.setSent(false);
            out.add(r);
        }
    }

    private LocalDate nextOccurrence(int month, int day, LocalDate today) {
        LocalDate first = LocalDate.of(today.getYear(), month, 1);
        int lastDay = first.lengthOfMonth();
        LocalDate candidate = first.withDayOfMonth(Math.min(day, lastDay));
        if (!candidate.isAfter(today)) {
            LocalDate firstNext = LocalDate.of(today.getYear() + 1, month, 1);
            candidate = firstNext.withDayOfMonth(Math.min(day, firstNext.lengthOfMonth()));
        }
        return candidate;
    }

    private LocalDate nextOccurrenceOf25th(LocalDate today) {
        LocalDate candidate = today.withDayOfMonth(25);
        if (!candidate.isAfter(today)) {
            candidate = candidate.plusMonths(2).withDayOfMonth(25);
        }
        return candidate;
    }

    @Transactional
    public int dispatchDue() {
        LocalDate today = LocalDate.now();
        List<ComplianceReminder> due = reminders.findBySentFalseAndTriggerDateLessThanEqual(today);
        int count = 0;
        for (ComplianceReminder r : due) {
            // Delivery (SMS/WhatsApp/email) is a future step.
            // Mark as sent in-app; replace with delivery service call later.
            r.setSent(true);
            reminders.save(r);
            count++;
        }
        return count;
    }
}
