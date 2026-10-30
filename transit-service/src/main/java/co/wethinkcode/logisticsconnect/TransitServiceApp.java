package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.StageSubscriber;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.Map;

public class TransitServiceApp {

    public static void main(String[] args) {
        StageCache stageCache = new StageCache();
        StageSubscriber subscriber = new StageSubscriber(stageCache::update);
        HubClient hubClient = new HubClient();

        Javalin app = Javalin.create().start(7053);
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/eta/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            try {
                HubInfo hub = hubClient.fetch(hubId);
                if (hub == null) {
                    ctx.status(404).json(error("Hub not found: " + hubId));
                    return;
                }

                StageEvent stageEvent = stageCache.find(hub.hubId())
                        .or(() -> stageCache.find(hubId))
                        .orElseGet(() -> new StageEvent(StageCache.normalize(hubId), 0, null));
                ctx.json(EtaCalculator.calculate(hub, stageEvent.stage()));
            } catch (IllegalStateException e) {
                ctx.status(503).json(error(e.getMessage()));
            } catch (Exception e) {
                ctx.status(502).json(error("Unable to calculate ETA: " + e.getMessage()));
            }
        });

        app.get("/delays/{hubId}", ctx -> {
            String hubId = ctx.pathParam("hubId");
            StageEvent event = stageCache.find(hubId)
                    .orElseGet(() -> new StageEvent(StageCache.normalize(hubId), 0, null));
            ctx.json(event);
        });

        app.get("/delays", ctx -> ctx.json(stageCache.all()));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            subscriber.close();
            app.stop();
        }));
    }

    private static Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
