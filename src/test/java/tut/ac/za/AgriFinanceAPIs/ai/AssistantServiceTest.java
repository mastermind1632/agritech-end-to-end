package tut.ac.za.AgriFinanceAPIs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class AssistantServiceTest {
    @Test
    void unknownStockPrivateRecordsAndTransactionsNeverDependOnModelClaims() {
        assertThat(AssistantService.boundedAnswer("Are seeds in stock and will delivery arrive tomorrow?"))
                .contains("not recorded", "cannot confirm");
        assertThat(AssistantService.boundedAnswer("Buy maize seed for me and place an order now"))
                .contains("cannot buy", "explicitly confirm");
        assertThat(AssistantService.boundedAnswer("Show another farmer's ledger expenses"))
                .contains("cannot share", "private");
        assertThat(AssistantService.boundedAnswer("How do I choose crop seed?")).isNull();
    }
    private FarmKnowledgeService knowledge() {
        FarmKnowledgeService knowledge = mock(FarmKnowledgeService.class);
        when(knowledge.retrieve(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(
                new FarmKnowledgeService.Snippet("guide:rotation", "Crop rotation", "Alternate crop families.")));
        return knowledge;
    }

    @Test
    void rejectsBlankModelAnswers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AssistantService service = new AssistantService(builder, "http://ollama.test", "qwen2.5:0.5b", knowledge(), "http://langchain.test");
        server.expect(requestTo("http://langchain.test/api/generate"))
                .andRespond(withSuccess("""
                        {"model":"qwen2.5:0.5b","response":"   "}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.chat("Hello", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ollama returned an empty response");
        server.verify();
    }

    @Test
    void sendsGroundedPromptThroughLangChain() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AssistantService service = new AssistantService(builder, "http://ollama.test", "qwen2.5:0.5b", knowledge(), "http://langchain.test");

        server.expect(requestTo("http://langchain.test/api/generate"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("qwen2.5:0.5b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.system").value(org.hamcrest.Matchers.containsString("ONLY for agriculture")))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString("Explain crop rotation")))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString("Source: Crop rotation")))
                .andRespond(withSuccess("""
                        {"model":"qwen2.5:0.5b","response":"Crop rotation alternates crops."}
                        """, MediaType.APPLICATION_JSON));

        ChatResponse response = service.chat("Explain crop rotation", null);

        assertThat(response.response()).isEqualTo("Crop rotation alternates crops.");
        server.verify();
    }

    @Test
    void pullsMissingModelThenRetriesPrompt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AssistantService service = new AssistantService(builder, "http://ollama.test", "qwen2.5:0.5b", knowledge(), "http://langchain.test");

        server.expect(requestTo("http://langchain.test/api/generate"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model not found, try pulling it first\"}"));
        server.expect(requestTo("http://ollama.test/api/pull"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"name":"qwen2.5:0.5b","stream":false}
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://langchain.test/api/generate"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {"model":"qwen2.5:0.5b","response":"The model is ready now."}
                        """, MediaType.APPLICATION_JSON));

        ChatResponse response = service.chat("Explain crop rotation", null);

        assertThat(response.response()).isEqualTo("The model is ready now.");
        server.verify();
    }

    @Test
    void retrievesSourcesBeforeGenerationAndIncludesFarmerFigures() {
        FarmKnowledgeService knowledge = mock(FarmKnowledgeService.class);
        when(knowledge.retrieve("What seed can I buy?")).thenReturn(java.util.List.of(
                new FarmKnowledgeService.Snippet("product:7", "Maize seed 5kg", "Listed price: R125")));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AssistantService service = new AssistantService(builder, "http://ollama.test", "qwen2.5:0.5b", knowledge, "http://langchain.test");
        server.expect(requestTo("http://langchain.test/api/generate"))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString("Listed price: R125")))
                .andExpect(jsonPath("$.prompt").value(org.hamcrest.Matchers.containsString("Expenses R400")))
                .andExpect(jsonPath("$.system").value(org.hamcrest.Matchers.containsString("untrusted DATA")))
                .andRespond(withSuccess("{\"model\":\"qwen2.5:0.5b\",\"response\":\"Maize seed is listed at R125.\"}", MediaType.APPLICATION_JSON));
        ChatResponse response = service.chat("What seed can I buy?", "Expenses R400");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().id()).isEqualTo("product:7");
        assertThat(response.sources().getFirst().publisher()).isEqualTo("AgriTech");
        server.verify();
    }

    @Test
    void unmatchedQuestionsNeverReachAnUnrestrictedModel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AssistantService service = new AssistantService(builder, "http://ollama.test", "qwen2.5:0.5b", mock(FarmKnowledgeService.class), "http://langchain.test");
        ChatResponse response = service.chat("Write a Python cloud server deployment command", null);
        assertThat(response.model()).isEqualTo("AgriTech");
        assertThat(response.response()).contains("farming and the AgriTech app");
        assertThat(response.sources()).isEmpty();
        server.verify();
    }
}
