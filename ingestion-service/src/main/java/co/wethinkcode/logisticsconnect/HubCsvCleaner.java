package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cleans the messy legacy export {@code hubs-global.csv} and deduplicates it.
 *
 * <p>What it fixes, per the issue list in {@code ingestion-service/README.md}:
 * <ul>
 *   <li><b>Padding / double spaces</b> — every field is trimmed and internal
 *       whitespace runs are collapsed to a single space.</li>
 *   <li><b>Casing</b> — ids are upper-cased ({@code h-501} -&gt; {@code H-501}),
 *       names are title-cased ({@code gauteng} -&gt; {@code Gauteng}).</li>
 *   <li><b>Spelling variants</b> — names are matched on a squashed key so
 *       {@code Kwa-Zulu Natal} / {@code KwaZulu Natal} / {@code KwaZulu-Natal}
 *       all collapse onto one canonical spelling (see {@link #NAME_ALIASES}).</li>
 *   <li><b>Booleans</b> — {@code Y/yes/1/TRUE/...} become {@code true},
 *       {@code N/no/0/FALSE/...} become {@code false}.</li>
 *   <li><b>Placeholders</b> — blank, {@code N/A}, {@code unknown}, {@code TBD},
 *       {@code -}, {@code NaN} and friends become null instead of being passed
 *       through as if they were real values.</li>
 *   <li><b>Garbage values</b> — anything non-placeholder that still makes no
 *       sense (an unknown boolean token, an impossible date) becomes null and is
 *       counted in {@link IngestionStats#invalidValues()}.</li>
 *   <li><b>Dates</b> — if the export ever grows a date-like column it is
 *       normalized to ISO {@code yyyy-MM-dd} by {@link DateNormalizer}; the
 *       bundled export has none, so {@code lastUpdated} stays null.</li>
 * </ul>
 *
 * <p><b>Deduplication strategy.</b> Rows describe the same real-world facility
 * when they name the same <i>sorting centre</i>: in this dataset the centre name
 * identifies the physical facility, while {@code province} is a secondary
 * attribute that is blank (H-508) or written in different spellings on rows we
 * otherwise know to be duplicates. So rows are grouped by squashed centre name,
 * then split by province where a name is genuinely shared by two provinces; rows
 * with no province are attached when the centre clearly belongs to a single
 * province, and otherwise kept separate.
 *
 * <p>Within a group the <i>first row in file order survives</i> (stable ids — the
 * earliest id is the canonical one), missing fields are filled from the other
 * rows, and a conflicting {@code active} flag is resolved by <b>majority vote</b>
 * over the non-placeholder values, ties going to the first value recorded.
 * Majority vote was chosen over "newest wins" (no usable timestamps in this
 * export) and "inactive wins" (assumes a business rule the data doesn't state):
 * duplicates are clerical re-exports of the same facility, so the flag repeated
 * most often is the most likely truth. Every folded row stays visible on
 * {@code /report} with a {@code duplicateOf} pointer — nothing is dropped in
 * silence.
 */
public final class HubCsvCleaner {

    /** Tokens accepted as {@code true} / {@code false} (compared lower-cased). */
    static final Set<String> TRUE_TOKENS = Set.of("y", "yes", "true", "t", "1");
    static final Set<String> FALSE_TOKENS = Set.of("n", "no", "false", "f", "0");

    /** Values that mean "no data" rather than anything real (lower-cased). */
    static final Set<String> PLACEHOLDERS = Set.of(
            "", "-", "--", "?", "n/a", "n.a.", "na", "nan", "null", "nil",
            "none", "tbd", "unknown", "undefined");

    /** Canonical spellings for known naming variants, keyed on the squashed name. */
    static final Map<String, String> NAME_ALIASES = Map.of(
            "kwazulunatal", "KwaZulu-Natal");

    private static final String MISSING_PROVINCE = "\u0000missing";

    public IngestionReport clean(String csv) {
        List<String[]> rawRows = readRows(csv);
        if (rawRows.isEmpty()) {
            throw new IllegalArgumentException("CSV has no header row");
        }
        Map<String, Integer> columns = headerIndex(rawRows.get(0));
        int hubCol = requireColumn(columns, "hubid");
        int provinceCol = requireColumn(columns, "province");
        int centerCol = requireColumn(columns, "sortingcenter");
        int activeCol = requireColumn(columns, "active");
        int dateCol = findDateColumn(rawRows.get(0));

        int rowsRead = 0;
        int rowsSkipped = 0;
        int fieldsNormalized = 0;
        int booleansNormalized = 0;
        int placeholdersReplaced = 0;
        int invalidValues = 0;

        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            String[] cells = rawRows.get(i);
            if (isBlankRow(cells)) {
                continue;
            }
            rowsRead++;

            String rawHubId = cell(cells, hubCol);
            String hubId = isPlaceholder(token(rawHubId)) ? null : normalizeHubId(rawHubId);
            if (hubId == null) {
                rowsSkipped++;
                placeholdersReplaced++;
                continue;
            }
            if (!hubId.equals(rawHubId)) {
                fieldsNormalized++;
            }

            String rawProvince = cell(cells, provinceCol);
            String province;
            if (isPlaceholder(token(rawProvince))) {
                placeholdersReplaced++;
                province = null;
            } else {
                province = normalizeName(rawProvince);
                if (!province.equals(rawProvince)) {
                    fieldsNormalized++;
                }
            }

            String rawCenter = cell(cells, centerCol);
            String sortingCenter;
            if (isPlaceholder(token(rawCenter))) {
                placeholdersReplaced++;
                sortingCenter = null;
            } else {
                sortingCenter = normalizeName(rawCenter);
                if (!sortingCenter.equals(rawCenter)) {
                    fieldsNormalized++;
                }
            }

            String rawActive = cell(cells, activeCol);
            String activeToken = token(rawActive);
            Boolean active;
            if (isPlaceholder(activeToken)) {
                placeholdersReplaced++;
                active = null;
            } else if (TRUE_TOKENS.contains(activeToken)) {
                active = true;
                if (!collapse(rawActive).equals("true")) {
                    booleansNormalized++;
                }
            } else if (FALSE_TOKENS.contains(activeToken)) {
                active = false;
                if (!collapse(rawActive).equals("false")) {
                    booleansNormalized++;
                }
            } else {
                invalidValues++;
                active = null;
            }

            String lastUpdated = null;
            if (dateCol >= 0) {
                String rawDate = cell(cells, dateCol);
                if (isPlaceholder(token(rawDate))) {
                    placeholdersReplaced++;
                } else {
                    lastUpdated = DateNormalizer.normalize(rawDate);
                    if (lastUpdated == null) {
                        invalidValues++;
                    } else if (!lastUpdated.equals(rawDate)) {
                        fieldsNormalized++;
                    }
                }
            }

            rows.add(new Row(rows.size(), hubId, province, sortingCenter, active, lastUpdated));
        }

        int duplicatesMerged = dedupe(rows);

        List<HubRecord> hubs = new ArrayList<>();
        List<CleanedRow> audited = new ArrayList<>();
        for (Row row : rows) {
            if (!row.duplicate) {
                hubs.add(row.merged);
            }
            audited.add(new CleanedRow(row.hubId, row.province, row.sortingCenter, row.active,
                    row.lastUpdated, row.duplicate, row.duplicateOf));
        }

        IngestionStats stats = new IngestionStats(rowsRead, rowsSkipped, fieldsNormalized,
                booleansNormalized, placeholdersReplaced, invalidValues, duplicatesMerged, hubs.size());
        return new IngestionReport(hubs, audited, stats);
    }

    // ------------------------------------------------------------------ dedup

    /** Folds duplicate rows into their surviving record, flags them, returns merged-away count. */
    private static int dedupe(List<Row> rows) {
        List<List<Row>> groups = groupDuplicates(rows);
        int merged = 0;
        for (List<Row> group : groups) {
            Row survivor = group.get(0);
            survivor.merged = merge(group);
            for (int i = 1; i < group.size(); i++) {
                Row other = group.get(i);
                other.duplicate = true;
                other.duplicateOf = survivor.hubId;
                merged++;
            }
        }
        return merged;
    }

    private static List<List<Row>> groupDuplicates(List<Row> rows) {
        Map<String, Map<String, List<Row>>> byCenter = new LinkedHashMap<>();
        List<List<Row>> groups = new ArrayList<>();
        for (Row row : rows) {
            if (row.sortingCenter == null) {
                groups.add(List.of(row)); // no facility name to match on — never dedup
                continue;
            }
            String centerKey = squashed(row.sortingCenter);
            String provinceKey = row.province == null ? MISSING_PROVINCE : squashed(row.province);
            byCenter.computeIfAbsent(centerKey, key -> new LinkedHashMap<>())
                    .computeIfAbsent(provinceKey, key -> new ArrayList<>())
                    .add(row);
        }
        for (Map<String, List<Row>> byProvince : byCenter.values()) {
            List<Row> noProvince = byProvince.remove(MISSING_PROVINCE);
            if (noProvince != null && byProvince.size() == 1) {
                // One clear owner for this centre name: attach the province-less rows to it.
                List<Row> owner = byProvince.values().iterator().next();
                owner.addAll(noProvince);
                owner.sort(Comparator.comparingInt(row -> row.index));
                groups.add(owner);
            } else {
                // Either the name is genuinely shared by several provinces, or we
                // have only province-less rows — keep what we cannot attribute apart.
                if (noProvince != null) {
                    groups.add(noProvince);
                }
                groups.addAll(byProvince.values());
            }
        }
        return groups;
    }

    private static HubRecord merge(List<Row> group) {
        Row survivor = group.get(0); // group is kept in file order
        return new HubRecord(
                survivor.hubId,
                firstPresent(group, row -> row.province),
                firstPresent(group, row -> row.sortingCenter),
                majorityVote(group),
                firstPresent(group, row -> row.lastUpdated));
    }

    private static Boolean majorityVote(List<Row> group) {
        long yes = group.stream().filter(row -> Boolean.TRUE.equals(row.active)).count();
        long no = group.stream().filter(row -> Boolean.FALSE.equals(row.active)).count();
        if (yes > no) {
            return Boolean.TRUE;
        }
        if (no > yes) {
            return Boolean.FALSE;
        }
        return group.stream().map(row -> row.active).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private static <T> T firstPresent(List<Row> group, java.util.function.Function<Row, T> field) {
        return group.stream().map(field).filter(Objects::nonNull).findFirst().orElse(null);
    }

    // ------------------------------------------------------------- normalizing

    /** {@code "  h - 501 " } -&gt; {@code "H-501"}; null when the value is a placeholder. */
    static String normalizeHubId(String raw) {
        String collapsed = collapse(raw).replaceAll("\\s*-\\s*", "-");
        return collapsed.isEmpty() ? null : collapsed.toUpperCase(Locale.ROOT);
    }

    /** Trims, collapses whitespace, title-cases and applies known spelling aliases. */
    static String normalizeName(String raw) {
        String titled = titleCase(collapse(raw));
        return NAME_ALIASES.getOrDefault(squashed(titled), titled);
    }

    private static String collapse(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    private static String titleCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        int wordStart = 0;
        for (int i = 0; i <= value.length(); i++) {
            if (i == value.length() || value.charAt(i) == ' ') {
                if (wordStart < i) {
                    if (result.length() > 0) {
                        result.append(' ');
                    }
                    appendWord(result, value.substring(wordStart, i));
                }
                wordStart = i + 1;
            }
        }
        return result.toString();
    }

    private static void appendWord(StringBuilder out, String word) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == '-') {
                out.append('-');
            } else if (i == 0 || word.charAt(i - 1) == '-') {
                out.append(Character.toUpperCase(c));
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
    }

    static String squashed(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String token(String raw) {
        return collapse(raw).toLowerCase(Locale.ROOT);
    }

    private static boolean isPlaceholder(String token) {
        return PLACEHOLDERS.contains(token);
    }

    // ------------------------------------------------------------------ parsing

    private static List<String[]> readRows(String csv) {
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            List<String[]> all = new ArrayList<>();
            String[] line;
            while ((line = reader.readNext()) != null) {
                all.add(line);
            }
            return all;
        } catch (IOException | CsvValidationException e) {
            throw new IllegalArgumentException("Could not parse hubs CSV: " + e.getMessage(), e);
        }
    }

    private static Map<String, Integer> headerIndex(String[] header) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.putIfAbsent(squashed(header[i]), i);
        }
        return columns;
    }

    private static int requireColumn(Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) {
            throw new IllegalArgumentException("CSV is missing required column '" + name
                    + "' — found " + columns.keySet());
        }
        return index;
    }

    private static int findDateColumn(String[] header) {
        for (int i = 0; i < header.length; i++) {
            if (DateNormalizer.looksLikeDateColumn(header[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String cell(String[] cells, int column) {
        return column < cells.length ? cells[column] : "";
    }

    private static boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (!collapse(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static final class Row {
        final int index;
        final String hubId;
        final String province;
        final String sortingCenter;
        final Boolean active;
        final String lastUpdated;
        boolean duplicate;
        String duplicateOf;
        HubRecord merged;

        Row(int index, String hubId, String province, String sortingCenter,
            Boolean active, String lastUpdated) {
            this.index = index;
            this.hubId = hubId;
            this.province = province;
            this.sortingCenter = sortingCenter;
            this.active = active;
            this.lastUpdated = lastUpdated;
        }
    }
}
