package co.wethinkcode.logisticsconnect;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class EtaCalculator {

    private static final int BASE_MINUTES = 60;
    private static final int WINDOW_MINUTES = 30;

    private EtaCalculator() {
    }

    public static EtaResponse calculate(HubInfo hub, int stage) {
        return calculate(hub, stage, Instant.now());
    }

    public static EtaResponse calculate(HubInfo hub, int stage, Instant now) {
        int delayMinutes = delayMinutes(stage);
        Instant etaStart = now.plus(BASE_MINUTES + delayMinutes, ChronoUnit.MINUTES);
        Instant etaEnd = etaStart.plus(WINDOW_MINUTES, ChronoUnit.MINUTES);

        return new EtaResponse(
                hub.hubId(),
                hub.sortingCenter(),
                hub.province(),
                stage,
                stageDescription(stage),
                delayMinutes,
                etaStart.toString(),
                etaEnd.toString(),
                status(stage),
                now.toString()
        );
    }

    static int delayMinutes(int stage) {
        return switch (stage) {
            case 0 -> 0;
            case 1 -> 30;
            case 2 -> 60;
            case 3 -> 120;
            case 4 -> 240;
            case 5 -> 480;
            case 6 -> 720;
            case 7 -> 1440;
            case 8 -> 2880;
            default -> throw new IllegalArgumentException("stage must be between 0 and 8");
        };
    }

    static String stageDescription(int stage) {
        return switch (stage) {
            case 0 -> "Normal operations";
            case 1 -> "Minor weather disruption";
            case 2 -> "Moderate weather disruption";
            case 3 -> "Significant weather disruption";
            case 4 -> "Partial hub shutdown";
            case 5 -> "Extended hub shutdown";
            case 6 -> "Severe regional disruption";
            case 7 -> "Full day closure";
            case 8 -> "Multi-day closure";
            default -> throw new IllegalArgumentException("stage must be between 0 and 8");
        };
    }

    static String status(int stage) {
        if (stage == 0) {
            return "ON_TIME";
        }
        if (stage <= 3) {
            return "DELAYED";
        }
        return "SEVERELY_DELAYED";
    }
}
