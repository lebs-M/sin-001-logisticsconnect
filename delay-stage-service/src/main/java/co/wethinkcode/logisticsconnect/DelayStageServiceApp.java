package co.wethinkcode.logisticsconnect;

import io.javalin.Javalin;
import io.javalin.http.Handler;

import java.util.LinkedHashMap;
import java.util.Map;

public class DelayStageServiceApp {

    public static void main(String[] args) {
        DelayStageStore store = new DelayStageStore();
        StagePublisher publisher = new StagePublisher();

        Javalin app = Javalin.create().start(7052);
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/delay-stage/{hubId}", ctx -> ctx.json(store.get(ctx.pathParam("hubId"))));

        Handler updateStage = ctx -> {
            String hubId = ctx.pathParam("hubId");
            StageRequest request;
            try {
                request = ctx.bodyAsClass(StageRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(error("Request body must be JSON like {\"stage\": 3}"));
                return;
            }

            if (request == null || request.stage() == null) {
                ctx.status(400).json(error("Field 'stage' is required"));
                return;
            }

            try {
                StageEvent event = store.update(hubId, request.stage());
                publisher.publish(event);
                ctx.json(event);
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(error(e.getMessage()));
            }
        };

        app.post("/delay-stage/{hubId}", updateStage);
        app.put("/delay-stage/{hubId}", updateStage);
        app.get("/delay-stages", ctx -> ctx.json(store.all()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            publisher.close();
            app.stop();
        }));
    }

    public record StageRequest(Integer stage) {
    }

    private static Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
