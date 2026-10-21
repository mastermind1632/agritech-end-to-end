package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.stereotype.Component;
import tut.ac.za.AgriFinanceAPIs.marketplace.MarketDisputeRepository;

/** Who may open a vault document besides its owner: admins, and both parties of a dispute it is evidence for. */
@Component
public class VaultAccess {

    private final MarketDisputeRepository disputes;

    public VaultAccess(MarketDisputeRepository disputes) {
        this.disputes = disputes;
    }

    public boolean canView(VaultDocumentInfo doc, String viewer, boolean admin) {
        if (admin || doc.ownerId().equals(viewer)) return true;
        if ("DISPUTE".equals(doc.linkedModule()) && doc.linkedRef() != null) {
            try {
                return disputes.findById(Long.parseLong(doc.linkedRef()))
                        .map(d -> viewer.equals(d.getRaisedBy()) || viewer.equals(d.getAgainstId()))
                        .orElse(false);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
