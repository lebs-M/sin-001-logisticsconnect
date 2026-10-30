package co.wethinkcode.logisticsconnect;

import co.wethinkcode.logisticsconnect.mq.StageSubscriber;
import io.javalin.Javalin;

import java.util.Map;

public class AlertBotApp {

    private static final int DEFAULT_ALERT_THRESHOLD = 4;

    public static void main(String[] args) {
        AlertService alertService = new AlertService(configuredThreshold());
        StageSubscriber subscriber = new StageSubscriber(alertService::handle);

        Javalin app = Javalin.create().start(7054);
        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alerts", ctx -> ctx.json(alertService.all()));
        app.get("/config", ctx -> ctx.json(Map.of(
                "alertThreshold", alertService.threshold(),
                "channel", "simulated-social-media"
        )));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            subscriber.close();
            app.stop();
        }));
    }

    private static int configuredThreshold() {
        String raw = System.getenv("ALERT_STAGE_THRESHOLD");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ALERT_THRESHOLD;
        }
        try {
            int threshold = Integer.parseInt(raw.trim());
            if (threshold < 0 || threshold > 8) {
                return DEFAULT_ALERT_THRESHOLD;
            }
            return threshold;
        } catch (NumberFormatException e) {
            return DEFAULT_ALERT_THRESHOLD;
        }
    }
}
