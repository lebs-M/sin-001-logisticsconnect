package co.wethinkcode.logisticsconnect;

/**
 * Counters describing what the cleaner actually did to the raw export, so the
 * cleaning is observable instead of a black box.
 *
 * @param rowsRead          data rows read from the CSV (header excluded)
 * @param rowsSkipped       rows dropped because they had no usable hub id
 * @param fieldsNormalized  text fields whose value changed (padding, casing,
 *                          collapsed double spaces, alias spellings)
 * @param booleansNormalized boolean fields whose written form changed (Y, 1,
 *                          TRUE, ... -> true/false); already-canonical values
 *                          are not counted
 * @param placeholdersReplaced fields that held a blank/N-A/unknown style
 *                          placeholder and were nulled out
 * @param invalidValues     non-placeholder values that made no sense (unknown
 *                          boolean token, unparseable date) and were nulled out
 * @param duplicatesMerged  rows folded into another record during dedup
 * @param recordsExposed    cleaned, deduplicated records served on /hubs
 */
public record IngestionStats(
        int rowsRead,
        int rowsSkipped,
        int fieldsNormalized,
        int booleansNormalized,
        int placeholdersReplaced,
        int invalidValues,
        int duplicatesMerged,
        int recordsExposed) {
}
