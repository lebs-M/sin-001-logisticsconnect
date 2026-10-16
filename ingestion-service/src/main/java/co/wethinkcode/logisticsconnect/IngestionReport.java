package co.wethinkcode.logisticsconnect;

import java.util.List;

/**
 * Everything the cleaner produced from one run over {@code hubs-global.csv}.
 *
 * @param hubs  deduplicated records, in first-appearance order (what /hubs serves)
 * @param rows  every source row after cleaning, duplicates flagged (what /report serves)
 * @param stats what the cleaner changed, see {@link IngestionStats}
 */
public record IngestionReport(List<HubRecord> hubs, List<CleanedRow> rows, IngestionStats stats) {
}


