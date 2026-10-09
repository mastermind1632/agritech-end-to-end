package tut.ac.za.AgriFinanceAPIs.ai;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tut.ac.za.AgriFinanceAPIs.finance.FinanceService;
import tut.ac.za.AgriFinanceAPIs.finance.dto.FinanceSummary;

class WorkflowControllerTest {
    @Test void retrievesOnlyAuthenticatedFiguresAndIgnoresSubmittedContext() {
        var finance=mock(FinanceService.class);var knowledge=mock(FarmKnowledgeService.class);
        var auth=mock(Authentication.class);when(auth.getName()).thenReturn("own");
        when(finance.getSummary("own")).thenReturn(new FinanceSummary("own",BigDecimal.TEN,BigDecimal.ONE));
        when(knowledge.retrieve("seed")).thenReturn(List.of(new FarmKnowledgeService.Snippet("guide:seed","Seeds","Crop seed")));
        var controller=new WorkflowController(RestClient.builder(),finance,knowledge,"http://workflow.test");
        var result=controller.grounding(new WorkflowController.GroundingQuery("seed",null),auth);
        assertThat(result.context()).contains("10").doesNotContain("Untrusted");
        assertThat(result.sources()).hasSize(1);
        assertThat(controller.identity(auth)).containsEntry("farmerId","own");
        verify(finance).getSummary("own");
    }
    @Test void forwardsBearerButNotCallerSuppliedIdentity() {
        var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();
        var controller=new WorkflowController(builder,mock(FinanceService.class),mock(FarmKnowledgeService.class),"http://workflow.test");
        UUID id=UUID.randomUUID();
        server.expect(requestTo("http://workflow.test/api/conversations/"+id+"/turn"))
            .andExpect(header("Authorization","Bearer verified-jwt"))
            .andExpect(jsonPath("$.prompt").value("crop seed"))
            .andRespond(withSuccess("{\"response\":\"Crop seed\"}",MediaType.APPLICATION_JSON));
        controller.turn(id,new WorkflowController.Turn("crop seed",null),"Bearer verified-jwt");
        server.verify();
    }
}
