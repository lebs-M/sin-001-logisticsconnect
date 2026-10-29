package co.wethinkcode.logisticsconnect;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class DelayStageStore {

    public static final int MIN_STAGE = 0;
    public static final int MAX_STAGE = 8;

    private final ConcurrentHashMap<String, StageEvent> stages = new ConcurrentHashMap<>();

    public StageEvent get(String hubId) {
        String key = normalize(hubId);
        return stages.getOrDefault(key, new StageEvent(key, 0, null));
    }

    public StageEvent update(String hubId, int stage) {
        validate(stage);
        StageEvent event = new StageEvent(normalize(hubId), stage, Instant.now().toString());
        stages.put(event.hubId(), event);
        return event;
    }

    public List<StageEvent> all() {
        return stages.values().stream()
                .sorted(Comparator.comparing(StageEvent::hubId))
                .toList();
    }

    public static void validate(int stage) {
        if (stage < MIN_STAGE || stage > MAX_STAGE) {
            throw new IllegalArgumentException(
                    "stage must be between " + MIN_STAGE + " and " + MAX_STAGE
            );
        }
    }

    public static String normalize(String hubId) {
        if (hubId == null) {
            return "";
        }
        return hubId.trim().toUpperCase(Locale.ROOT);
    }
}
