package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class HubClient {

    private static final String HUB_SERVICE_URL = System.getenv().getOrDefault(
            "HUB_SERVICE_URL", "http://localhost:7051"
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public HubInfo fetch(String hubId) throws Exception {
        String encodedId = URLEncoder.encode(hubId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(HUB_SERVICE_URL + "/hubs/" + encodedId))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "hub-service returned HTTP " + response.statusCode()
            );
        }
        return mapper.readValue(response.body(), HubInfo.class);
    }
}
