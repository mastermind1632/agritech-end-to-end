package tut.ac.za.AgriFinanceAPIs.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SegmentationClient {

    private final RestClient http;

    public SegmentationClient(RestClient.Builder builder,
                              @Value("${segmentation.base-url:http://segmentation-service:8002}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public String get(String path, String bearerToken) {
        return http.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    public String put(String path, String bearerToken, String jsonBody) {
        return http.put()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
    }

    public String post(String path, String bearerToken, String jsonBody) {
        return http.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
    }

    public String delete(String path, String bearerToken) {
        return http.delete()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }
}
