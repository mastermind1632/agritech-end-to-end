package tut.ac.za.AgriFinanceAPIs.ai;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import tut.ac.za.AgriFinanceAPIs.finance.FinanceService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
public class AssistantController {
    private final AssistantService chatService;
    private final FinanceService finance;

    public AssistantController(AssistantService chatService, FinanceService finance) {
        this.chatService = chatService;
        this.finance = finance;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication auth) {
        var summary = finance.getSummary(auth.getName());
        return chatService.chat(request.prompt(), "Verified current farmer ledger summary in ZAR: income="
                + summary.getTotalIncome() + "; expenses=" + summary.getTotalExpenses()
                + "; profit=" + summary.getProfit() + ". These totals cover recorded entries only.");
    }
}
