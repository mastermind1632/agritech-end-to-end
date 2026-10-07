package tut.ac.za.AgriFinanceAPIs.recommendation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tut.ac.za.AgriFinanceAPIs.farmer.*;
import tut.ac.za.AgriFinanceAPIs.finance.*;
import tut.ac.za.AgriFinanceAPIs.grouporder.*;
import tut.ac.za.AgriFinanceAPIs.supplier.*;

import java.util.*;
import java.util.stream.*;

@Service
public class RecommendationService {

    private final AiRecommendationRepository recs;
    private final FarmerRepository farmers;
    private final ExpenseRepository expenses;
    private final SupplierProductRepository products;
    private final GroupOrderRepository orders;
    private final GroupOrderItemRepository items;

    public RecommendationService(
            AiRecommendationRepository r,
            FarmerRepository f,
            ExpenseRepository e,
            SupplierProductRepository p,
            GroupOrderRepository o,
            GroupOrderItemRepository i) {

        recs = r;
        farmers = f;
        expenses = e;
        products = p;
        orders = o;
        items = i;
    }

    public List<AiRecommendation> forFarmer(String farmerId) {

        ensureFarmer(farmerId);

        return recs.findAllByFarmerIdOrderByCreatedAtDesc(farmerId);
    }

    public List<AiRecommendation> generate(String farmerId) {

        Farmer farmer = ensureFarmer(farmerId);

        List<SupplierProduct> ps = products.findAll();

        List<GroupOrder> open =
                orders.findAllByStatusOrderByCreatedAtDesc("open");

        String expenseText = expenses
                .findAllByFarmerIdOrderByDateDesc(farmerId)
                .stream()
                .map(Expense::getItem)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase();

        List<AiRecommendation> result = new ArrayList<>();

        for (GroupOrder o : open) {

            SupplierProduct p =
                    products.findById(o.getProductId()).orElse(null);

            if (p == null) {
                continue;
            }

            boolean activity =
                    expenseText.contains(
                            p.getProductName().toLowerCase()
                    );

            long sameGroupFarmers =
                    items.findAllByGroupOrderIdOrderByJoinedAtAsc(o.getId())
                            .stream()
                            .filter(i -> !farmerId.equals(i.getFarmerId()))
                            .count();

            if (activity || sameGroupFarmers > 0) {

                AiRecommendation r = new AiRecommendation();

                r.setFarmerId(farmerId);
                r.setRecommendedProductId(p.getId());
                r.setSuggestedGroupOrderId(o.getId());

                String reason;

                if (activity) {
                    reason = "Your recorded expenses suggest you use "
                            + p.getProductName() + ". ";
                } else {
                    reason = "Other farmers are already joining a group order for "
                            + p.getProductName() + ". ";
                }

                if (farmer.getLocation() != null
                        && !farmer.getLocation().isBlank()) {

                    reason += "A group purchase may help you coordinate "
                            + "a bulk purchase from the supplier.";

                } else {

                    reason += "A group purchase may help coordinate "
                            + "a bulk purchase from the supplier.";
                }

                r.setReason(reason);

                result.add(recs.save(r));
            }
        }

        return result;
    }

    private Farmer ensureFarmer(String id) {

        if (id == null || !farmers.existsById(id)) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Farmer not found"
            );
        }

        return farmers.findById(id).get();
    }
}

