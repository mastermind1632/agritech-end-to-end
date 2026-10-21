package tut.ac.za.AgriFinanceAPIs.statements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Talks to the Python document-service, which reads PDFs (text layer with pdfplumber) and scans or photos
 * (Tesseract OCR). It only returns text lines; parsing, validation and categorisation stay in Java.
 */
@Component
public class DocumentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceClient.class);

    public record Extraction(String method, Integer pages, List<String> lines, List<String> warnings) { }

    private final RestClient client;

    public DocumentServiceClient(RestClient.Builder builder, @Value("${app.document-service.url:http://localhost:8003}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(180));
        this.client = builder.clone().baseUrl(url).requestFactory(factory).build();
    }

    /** Returns null when the service is not running, so the caller can explain that only CSV works right now. */
    public Extraction extract(String fileName, byte[] bytes) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() { return fileName == null ? "statement" : fileName; }
        });
        try {
            return client.post().uri("/extract").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                    .retrieve().body(Extraction.class);
        } catch (RestClientException e) {
            log.warn("Document service unavailable: {}", e.getMessage());
            return null;
        }
    }
}
