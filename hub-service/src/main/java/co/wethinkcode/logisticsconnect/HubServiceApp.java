package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HubServiceApp {

    public static void main(String[] args) {
        HubRepository repository = new HubRepository();

        Javalin app = Javalin.create().start(7051);
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> {
            try {
                ctx.json(repository.all());
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        app.get("/hubs/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            try {
                repository.find(hubId)
                        .ifPresentOrElse(ctx::json, () -> ctx.status(404).json(error(
                                "Hub not found: " + hubId
                        )));
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        app.get("/provinces", ctx -> {
            try {
                List<String> provinces = repository.all().stream()
                        .map(Hub::province)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
                ctx.json(provinces);
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        app.get("/provinces/{province}/hubs", ctx -> {
            String province = ctx.pathParam("province");
            try {
                List<Hub> matching = repository.all().stream()
                        .filter(hub -> hub.province() != null
                                && hub.province().equalsIgnoreCase(province))
                        .toList();
                ctx.json(matching);
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        app.get("/sorting-centers", ctx -> {
            try {
                List<String> centers = repository.all().stream()
                        .map(Hub::sortingCenter)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
                ctx.json(centers);
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        app.post("/refresh", ctx -> {
            try {
                List<Hub> refreshed = repository.refresh();
                ctx.json(Map.of("refreshed", refreshed.size()));
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(app::stop));
    }

    private static Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
