package co.wethinkcode.logisticsconnect;

public record EtaResponse(
        String hubId,
        String sortingCenter,
        String province,
        int stage,
        String stageDescription,
        int delayMinutes,
        String etaStart,
        String etaEnd,
        String status,
        String calculatedAt
) {
}
