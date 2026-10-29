package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HubRepository {

    private static final String INGESTION_URL = System.getenv().getOrDefault(
            "INGESTION_SERVICE_URL", "http://localhost:7050"
    );
    private static final Pattern HUB_ID_PATTERN = Pattern.compile("^([A-Z]+)[\\s_-]*(\\d+)$");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile List<Hub> hubs = List.of();
    private volatile Map<String, Hub> hubsById = Map.of();

    public List<Hub> all() {
        ensureLoaded();
        return hubs;
    }

    public Optional<Hub> find(String hubId) {
        ensureLoaded();
        String key = normalizeKey(hubId);
        return Optional.ofNullable(hubsById.get(key));
    }

    public synchronized List<Hub> refresh() {
        load();
        return hubs;
    }

    private synchronized void ensureLoaded() {
        if (hubs.isEmpty()) {
            load();
        }
    }

    private void load() {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(INGESTION_URL + "/hubs"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException(
                            "ingestion-service returned HTTP " + response.statusCode()
                    );
                }

                Hub[] loaded = mapper.readValue(response.body(), Hub[].class);
                List<Hub> loadedHubs = List.of(loaded);
                Map<String, Hub> index = new HashMap<>();
                for (Hub hub : loadedHubs) {
                    index.put(hub.hubId().toUpperCase(Locale.ROOT), hub);
                    if (hub.aliases() != null) {
                        for (String alias : hub.aliases()) {
                            index.put(alias.toUpperCase(Locale.ROOT), hub);
                        }
                    }
                }

                hubs = loadedHubs;
                hubsById = Map.copyOf(index);
                return;
            } catch (Exception e) {
                lastFailure = e;
                sleep(500);
            }
        }
        throw new IllegalStateException(
                "Unable to load hubs from ingestion-service at " + INGESTION_URL,
                lastFailure
        );
    }

    static String normalizeKey(String hubId) {
        if (hubId == null) {
            return "";
        }
        String cleaned = hubId.trim().toUpperCase(Locale.ROOT);
        if (cleaned.matches("^\\d+$")) {
            return "H-" + stripLeadingZeros(cleaned);
        }
        Matcher matcher = HUB_ID_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            return matcher.group(1) + "-" + stripLeadingZeros(matcher.group(2));
        }
        return cleaned;
    }

    private static String stripLeadingZeros(String value) {
        String stripped = value.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
