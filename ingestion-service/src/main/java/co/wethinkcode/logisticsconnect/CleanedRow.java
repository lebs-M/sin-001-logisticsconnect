package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One source row after cleaning, kept for auditability: duplicates are not
 * dropped silently, they are flagged and point at the record that won.
 *
 * @param hubId       normalized hub id (uppercase, trimmed)
 * @param province    normalized province, or null when the row had a placeholder
 * @param sortingCenter normalized sorting centre name, or null when placeholder
 * @param active      normalized boolean, or null when placeholder/unrecognized
 * @param lastUpdated ISO date when the row carried a date-like column, else null
 * @param duplicate   true when this row was folded into another record
 * @param duplicateOf hub id of the surviving record when {@code duplicate} is true
 */
public record CleanedRow(
        String hubId,
        String province,
        String sortingCenter,
        Boolean active,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastUpdated,
        boolean duplicate,
        String duplicateOf) {
}
