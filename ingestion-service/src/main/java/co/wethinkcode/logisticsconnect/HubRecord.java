package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single cleaned hub record as served by ingestion-service.
 *
 * <p>{@code province} and {@code active} are null when the source row only had a
 * placeholder value for them (blank, N/A, unknown, ...). {@code lastUpdated} is
 * only populated when the export actually contains a date-like column (the
 * bundled {@code hubs-global.csv} does not) and is omitted from JSON when null.
 */
public record HubRecord(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastUpdated) {

    public HubRecord(String hubId, String province, String sortingCenter, Boolean active) {
        this(hubId, province, sortingCenter, active, null);
    }
}
