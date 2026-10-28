package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IngestionServiceApp {

    public static void main(String[] args) throws Exception {
        HubCsvCleaner.CleanResult result = loadCleanedHubs();
        List<CleanedHub> hubs = result.hubs();
        Map<String, CleanedHub> hubsById = indexHubs(hubs);

        Javalin app = Javalin.create().start(7050);
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(hubs));

        app.get("/hubs/{hubId}", ctx -> {
            String requestedId = ctx.pathParam("hubId");
            String normalizedId = HubCsvCleaner.normalizeHubId(requestedId);
            CleanedHub hub = normalizedId == null ? null : hubsById.get(normalizedId);
            if (hub == null) {
                ctx.status(404).json(error("Hub not found", requestedId));
                return;
            }
            ctx.json(hub);
        });

        app.get("/stats", ctx -> ctx.json(Map.of(
                "rawRows", result.rawRowCount(),
                "cleanedHubs", hubs.size(),
                "duplicateRowsRemoved", result.duplicateRowsRemoved(),
                "skippedRows", result.skippedRows()
        )));

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    private static HubCsvCleaner.CleanResult loadCleanedHubs() throws Exception {
        try (InputStream input = IngestionServiceApp.class.getResourceAsStream("/hubs-global.csv")) {
            if (input == null) {
                throw new IllegalStateException("hubs-global.csv not found on the classpath");
            }
            HubCsvCleaner cleaner = new HubCsvCleaner();
            return cleaner.clean(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }

    private static Map<String, CleanedHub> indexHubs(List<CleanedHub> hubs) {
        Map<String, CleanedHub> index = new HashMap<>();
        for (CleanedHub hub : hubs) {
            index.put(hub.hubId().toUpperCase(Locale.ROOT), hub);
            for (String alias : hub.aliases()) {
                index.put(alias.toUpperCase(Locale.ROOT), hub);
            }
        }
        return Map.copyOf(index);
    }

    private static Map<String, Object> error(String message, String hubId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("hubId", hubId);
        return body;
    }
}
