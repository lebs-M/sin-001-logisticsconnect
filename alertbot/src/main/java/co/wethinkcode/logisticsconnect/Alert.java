package co.wethinkcode.logisticsconnect;

public record Alert(
        long id,
        String hubId,
        int stage,
        String message,
        String stageTimestamp,
        String postedAt,
        String channel,
        String status
) {
}
