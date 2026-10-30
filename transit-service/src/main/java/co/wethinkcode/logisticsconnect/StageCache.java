package co.wethinkcode.logisticsconnect;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StageCache {

    private final ConcurrentHashMap<String, StageEvent> stages = new ConcurrentHashMap<>();

    public void update(StageEvent event) {
        if (event != null && event.hubId() != null) {
            stages.put(normalize(event.hubId()), event);
        }
    }

    public Optional<StageEvent> find(String hubId) {
        return Optional.ofNullable(stages.get(normalize(hubId)));
    }

    public List<StageEvent> all() {
        return stages.values().stream()
                .sorted(Comparator.comparing(StageEvent::hubId))
                .toList();
    }

    public static String normalize(String hubId) {
        if (hubId == null) {
            return "";
        }
        return hubId.trim().toUpperCase(Locale.ROOT);
    }
}
