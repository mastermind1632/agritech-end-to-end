package tut.ac.za.AgriFinanceAPIs.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.finance.FinanceService;

@RestController
@RequestMapping("/api/ai")
public class WorkflowController {
    public record ReviewAction(@NotNull UUID orderId, @Min(1) @Max(1000000) int quantity) {}
    public record Turn(@Size(max=2000) String prompt, @Valid ReviewAction action) {}
    public record Approval(@NotBlank @Size(max=100) String interruptId, @NotNull Boolean approved) {}
    public record Passage(String id,String title,String content,String url,String publisher,
            String region,String checkedOn,String reviewStatus) {}
    public record Grounding(List<Passage> sources,String context,String system,String policyReply) {}
    public record GroundingQuery(@NotBlank @Size(max=2000) String prompt,@Size(max=2000) String searchQuery) {}
    private final RestClient gateway;
    private final FinanceService finance;
    private final FarmKnowledgeService knowledge;

    public WorkflowController(RestClient.Builder builder, FinanceService finance, FarmKnowledgeService knowledge,
            @Value("${ai.langchain-url}") String url) {
        gateway=builder.baseUrl(url).build();this.finance=finance;this.knowledge=knowledge;
    }

    @GetMapping("/identity")
    public Map<String,String> identity(Authentication auth) { return Map.of("farmerId",auth.getName()); }

    @PostMapping("/grounding")
    public Grounding grounding(@Valid @RequestBody GroundingQuery request, Authentication auth) {
        var summary=finance.getSummary(auth.getName());
        String query=request.searchQuery()==null || request.searchQuery().isBlank()?request.prompt():request.searchQuery();
        var sources=knowledge.retrieve(query).stream().map(snippet -> {
            var citation=FarmKnowledgeService.citation(snippet);
            return new Passage(snippet.id(),snippet.title(),snippet.content(),citation.url(),citation.publisher(),
                    citation.region(),citation.checkedOn(),citation.reviewStatus());
        }).toList();
        return new Grounding(sources,"Verified current farmer ledger summary in ZAR: income="
                +summary.getTotalIncome()+"; expenses="+summary.getTotalExpenses()+"; profit="+summary.getProfit()
                +". These totals cover recorded entries only.",AssistantService.SYSTEM,
                AssistantService.boundedAnswer(request.prompt()));
    }

    @PostMapping("/conversations")
    public Object create(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return send("/api/conversations",bearer,Map.of());
    }
    @GetMapping("/conversations/{id}")
    public Object get(@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.get().uri("/api/conversations/"+id).header(HttpHeaders.AUTHORIZATION,bearer)
                .retrieve().body(Object.class));
    }
    @DeleteMapping("/conversations/{id}")
    public Object delete(@PathVariable UUID id,@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.delete().uri("/api/conversations/"+id).header(HttpHeaders.AUTHORIZATION,bearer)
                .retrieve().body(Object.class));
    }
    @PostMapping("/conversations/{id}/turn")
    public Object turn(@PathVariable UUID id,@Valid @RequestBody Turn request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        if(request.action()==null && (request.prompt()==null || request.prompt().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ask a question or request an order review");
        return send("/api/conversations/"+id+"/turn",bearer,
                new Turn(request.prompt()==null?"":request.prompt(),request.action()));
    }
    @PostMapping("/conversations/{id}/resume")
    public Object resume(@PathVariable UUID id,@Valid @RequestBody Approval request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return send("/api/conversations/"+id+"/resume",bearer,request);
    }
    private Object send(String path,String bearer,Object body) {
        return proxy(() -> gateway.post().uri(path).header(HttpHeaders.AUTHORIZATION,bearer)
                .body(body).retrieve().body(Object.class));
    }
    private Object proxy(java.util.function.Supplier<Object> call) {
        try { return call.get(); }
        catch(RestClientResponseException exception) {
            int status=exception.getStatusCode().value();
            if(status!=400 && status!=401 && status!=403 && status!=404 && status!=409 && status!=429)status=502;
            throw new ResponseStatusException(HttpStatus.valueOf(status),
                    status==404?"Conversation or buying opportunity not found":
                    status==409?"Resolve the pending review or refresh the conversation":
                    status==429?"Too many assistant requests. Please wait a minute.":
                    "The farming workflow is unavailable. Please retry.",exception);
        } catch(RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"The farming workflow is unavailable",exception);
        }
    }
}
