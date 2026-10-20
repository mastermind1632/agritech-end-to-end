package tut.ac.za.AgriFinanceAPIs.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/finance/statement")
public class StatementAnalysisController {

    private final RestClient ocr;
    private final ObjectMapper mapper = new ObjectMapper();

    public StatementAnalysisController(RestClient.Builder builder,
                                       @Value("${ai.segmentation-url:http://segmentation-service:8002}") String segmentationUrl) {
        this.ocr = builder.baseUrl(segmentationUrl).build();
    }

    public record StatementLine(String date, String description, BigDecimal amount, String cropTag) {}
    public record StatementUploadRequest(
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            List<StatementLine> transactions
    ) {}

    @PostMapping("/analyze")
    public Map<String, Object> analyzeStatement(@RequestBody StatementUploadRequest request) {
        return analyze(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadAndAnalyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", required = false) String password,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String bearer) throws IOException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file");
        }
        if (bearer == null || bearer.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only PDF files are supported");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return filename; }
        });

        String json;
        try {
            String uri = password != null && !password.isBlank()
                    ? "/api/ocr/extract?password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
                    : "/api/ocr/extract";
            json = ocr.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String detail = ex.getResponseBodyAsString();
            throw new ResponseStatusException(HttpStatus.valueOf(status >= 400 && status < 600 ? status : 502),
                    detail.isBlank() ? "OCR service rejected the upload" : detail);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR service unavailable: " + ex.getMessage());
        }

        StatementUploadRequest parsed = parseOcrResponse(json);
        return ResponseEntity.ok(analyze(parsed));
    }

    private StatementUploadRequest parseOcrResponse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            BigDecimal opening = root.has("openingBalance") ? root.get("openingBalance").decimalValue() : BigDecimal.ZERO;
            BigDecimal closing = root.has("closingBalance") ? root.get("closingBalance").decimalValue() : BigDecimal.ZERO;
            List<StatementLine> lines = new ArrayList<>();
            if (root.has("transactions") && root.get("transactions").isArray()) {
                for (JsonNode t : root.get("transactions")) {
                    String date = t.has("date") ? t.get("date").asText() : "";
                    String desc = t.has("description") ? t.get("description").asText() : "Transaction";
                    BigDecimal amt = t.has("amount") ? t.get("amount").decimalValue() : BigDecimal.ZERO;
                    String crop = t.has("cropTag") && !t.get("cropTag").isNull() ? t.get("cropTag").asText() : null;
                    lines.add(new StatementLine(date, desc, amt, crop));
                }
            }
            return new StatementUploadRequest(opening, closing, lines);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse OCR response: " + ex.getMessage());
        }
    }

    private Map<String, Object> analyze(StatementUploadRequest request) {
        BigDecimal totalNet = request.transactions().stream()
                .map(StatementLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal calculatedClosing = request.openingBalance().add(totalNet);
        boolean balanceValid = calculatedClosing.compareTo(request.closingBalance()) == 0;

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> cropCostTotals = new LinkedHashMap<>();
        List<Map<String, Object>> categorizedTransactions = new ArrayList<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();

        for (StatementLine line : request.transactions()) {
            String category = categorize(line.description(), line.amount());
            BigDecimal absAmount = line.amount().abs();

            categoryTotals.merge(category, absAmount, BigDecimal::add);
            if (line.cropTag() != null && !line.cropTag().isBlank() && line.amount().compareTo(BigDecimal.ZERO) < 0) {
                cropCostTotals.merge(line.cropTag(), absAmount, BigDecimal::add);
            }

            boolean unusual = absAmount.compareTo(new BigDecimal("25000")) > 0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", line.date());
            item.put("description", line.description());
            item.put("amount", line.amount());
            item.put("category", category);
            item.put("cropTag", line.cropTag() == null ? "Unallocated" : line.cropTag());
            item.put("unusualFlag", unusual);
            categorizedTransactions.add(item);
            if (unusual) {
                anomalies.add(item);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balanceValid", balanceValid);
        result.put("expectedClosingBalance", calculatedClosing);
        result.put("reportedClosingBalance", request.closingBalance());
        result.put("status", balanceValid ? "VALIDATED" : "FLAGGED_FOR_REVIEW");
        result.put("categoryTotals", categoryTotals);
        result.put("cropCostTotals", cropCostTotals);
        result.put("anomalies", anomalies);
        result.put("transactions", categorizedTransactions);
        return result;
    }

    private String categorize(String description, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "Produce sales & income";
        }
        String desc = description == null ? "" : description.toLowerCase();
        if (desc.contains("engen") || desc.contains("shell") || desc.contains("sasol") || desc.contains("total") || desc.contains("diesel") || desc.contains("fuel")) {
            return "Fuel";
        }
        if (desc.contains("omnia") || desc.contains("kynoch") || desc.contains("fertil") || desc.contains("npk") || desc.contains("urea")) {
            return "Fertiliser";
        }
        if (desc.contains("seed") || desc.contains("stark") || desc.contains("sakata") || desc.contains("syngenta") || desc.contains("seedling")) {
            return "Seeds and seedlings";
        }
        if (desc.contains("spray") || desc.contains("chem") || desc.contains("pest") || desc.contains("herbicide")) {
            return "Agrochemicals";
        }
        if (desc.contains("wage") || desc.contains("payroll") || desc.contains("labour") || desc.contains("uif")) {
            return "Labour and wages";
        }
        if (desc.contains("eskom") || desc.contains("water") || desc.contains("municipality")) {
            return "Electricity and water";
        }
        if (desc.contains("fee") || desc.contains("charge") || desc.contains("bank")) {
            return "Bank fees";
        }
        return "General farm expense";
    }
}



