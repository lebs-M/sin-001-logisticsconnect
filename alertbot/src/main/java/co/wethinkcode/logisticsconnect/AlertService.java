package co.wethinkcode.logisticsconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final int threshold;
    private final AtomicLong nextId = new AtomicLong(1);
    private final List<Alert> alerts = Collections.synchronizedList(new ArrayList<>());

    public AlertService(int threshold) {
        this.threshold = threshold;
    }

    public Alert handle(StageEvent event) {
        if (event == null || event.stage() < threshold) {
            return null;
        }

        Alert alert = new Alert(
                nextId.getAndIncrement(),
                event.hubId(),
                event.stage(),
                "Delay stage " + event.stage() + " at hub " + event.hubId()
                        + ". Expect significant transit delays.",
                event.timestamp(),
                Instant.now().toString(),
                "simulated-social-media",
                "posted"
        );

        alerts.add(alert);
        log.info("SIMULATED SOCIAL POST | hub={} stage={} | {}", alert.hubId(), alert.stage(), alert.message());
        return alert;
    }

    public List<Alert> all() {
        synchronized (alerts) {
            return List.copyOf(alerts);
        }
    }

    public int threshold() {
        return threshold;
    }
}
