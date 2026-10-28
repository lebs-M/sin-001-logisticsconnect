package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HubCsvCleaner {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "n/a", "na", "n.a.", "tbd", "unknown", "-", "nan", "null", "none", "?"
    );

    private static final Map<String, String> PROVINCES = Map.ofEntries(
            Map.entry("gauteng", "Gauteng"),
            Map.entry("westerncape", "Western Cape"),
            Map.entry("easterncape", "Eastern Cape"),
            Map.entry("northerncape", "Northern Cape"),
            Map.entry("kwazulunatal", "KwaZulu-Natal"),
            Map.entry("freestate", "Free State"),
            Map.entry("northwest", "North West"),
            Map.entry("limpopo", "Limpopo"),
            Map.entry("mpumalanga", "Mpumalanga")
    );

    private static final Pattern HUB_ID_PATTERN = Pattern.compile("^([A-Z]+)[\\s_-]*0*(\\d+)$");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("^\\d+$");

    public CleanResult clean(Reader reader) throws IOException {
        List<RawRow> rows = new ArrayList<>();
        int rawRowCount = 0;

        try (CSVReader csv = new CSVReader(reader)) {
            String[] header;
            try {
                header = csv.readNext();
            } catch (CsvValidationException e) {
                throw new IOException("Invalid CSV header", e);
            }
            if (header == null) {
                return new CleanResult(List.of(), 0, 0, 0);
            }

            Map<String, Integer> columns = indexColumns(header);
            int idIndex = findColumn(columns, "hub_id", "id");
            int provinceIndex = findColumn(columns, "province");
            int centerIndex = findColumn(columns, "sorting_center", "sortingcentre", "sorting_centre");
            int activeIndex = findColumn(columns, "active");

            String[] line;
            while (true) {
                try {
                    line = csv.readNext();
                } catch (CsvValidationException e) {
                    throw new IOException("Invalid CSV row", e);
                }
                if (line == null) {
                    break;
                }
                if (isBlankRow(line)) {
                    continue;
                }
                rawRowCount++;

                String hubId = normalizeHubId(cell(line, idIndex));
                if (hubId == null) {
                    continue;
                }
                rows.add(new RawRow(
                        hubId,
                        normalizeProvince(cell(line, provinceIndex)),
                        normalizeSortingCenter(cell(line, centerIndex)),
                        parseBoolean(cell(line, activeIndex))
                ));
            }
        }

        Map<String, String> inferredProvinces = inferProvinces(rows);
        Map<String, List<ResolvedRow>> groups = new LinkedHashMap<>();

        for (RawRow row : rows) {
            String center = row.sortingCenter() == null ? "Unknown Sorting Center" : row.sortingCenter();
            String centerKey = center.toLowerCase(Locale.ROOT);
            String province = row.province() != null
                    ? row.province()
                    : inferredProvinces.getOrDefault(centerKey, "Unknown");
            String groupKey = centerKey + "|" + province.toLowerCase(Locale.ROOT);

            groups.computeIfAbsent(groupKey, key -> new ArrayList<>())
                    .add(new ResolvedRow(row.hubId(), province, center, row.active()));
        }

        List<CleanedHub> hubs = new ArrayList<>();
        for (List<ResolvedRow> group : groups.values()) {
            hubs.add(merge(group));
        }
        hubs.sort(Comparator.comparingInt(hub -> numericId(hub.hubId())));

        int duplicatesRemoved = rows.size() - hubs.size();
        int skippedRows = rawRowCount - rows.size();
        return new CleanResult(hubs, rawRowCount, duplicatesRemoved, skippedRows);
    }

    private CleanedHub merge(List<ResolvedRow> group) {
        ResolvedRow canonical = group.stream()
                .min(Comparator.comparingInt((ResolvedRow row) -> numericId(row.hubId()))
                        .thenComparing(ResolvedRow::hubId))
                .orElseThrow();

        Set<String> aliasSet = new LinkedHashSet<>();
        int trueCount = 0;
        int falseCount = 0;
        Boolean firstKnownActive = null;

        for (ResolvedRow row : group) {
            aliasSet.add(row.hubId());
            if (row.active() != null) {
                if (firstKnownActive == null) {
                    firstKnownActive = row.active();
                }
                if (row.active()) {
                    trueCount++;
                } else {
                    falseCount++;
                }
            }
        }

        Boolean active;
        if (trueCount == 0 && falseCount == 0) {
            active = null;
        } else if (trueCount == falseCount) {
            active = canonical.active() != null ? canonical.active() : firstKnownActive;
        } else {
            active = trueCount > falseCount;
        }

        List<String> aliases = aliasSet.stream()
                .sorted(Comparator.comparingInt(HubCsvCleaner::numericId).thenComparing(id -> id))
                .toList();

        return new CleanedHub(
                canonical.hubId(),
                canonical.province(),
                canonical.sortingCenter(),
                active,
                aliases
        );
    }

    private Map<String, String> inferProvinces(List<RawRow> rows) {
        Map<String, Map<String, Integer>> votes = new HashMap<>();

        for (RawRow row : rows) {
            if (row.sortingCenter() == null || row.province() == null) {
                continue;
            }
            String centerKey = row.sortingCenter().toLowerCase(Locale.ROOT);
            votes.computeIfAbsent(centerKey, key -> new LinkedHashMap<>())
                    .merge(row.province(), 1, Integer::sum);
        }

        Map<String, String> inferred = new HashMap<>();
        votes.forEach((center, provinceVotes) -> provinceVotes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> inferred.put(center, entry.getKey())));
        return inferred;
    }

    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String name = normalizeHeader(header[i]);
            if (!name.isEmpty()) {
                columns.putIfAbsent(name, i);
            }
        }
        return columns;
    }

    private static int findColumn(Map<String, Integer> columns, String... names) {
        for (String name : names) {
            Integer index = columns.get(name);
            if (index != null) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\uFEFF", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static boolean isBlankRow(String[] line) {
        for (String value : line) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(String[] line, int index) {
        if (index < 0 || index >= line.length) {
            return null;
        }
        return line[index];
    }

    static String normalizeHubId(String value) {
        if (isPlaceholder(value)) {
            return null;
        }
        String cleaned = collapseWhitespace(value).toUpperCase(Locale.ROOT);
        Matcher matcher = HUB_ID_PATTERN.matcher(cleaned);
        if (matcher.matches()) {
            return matcher.group(1) + "-" + matcher.group(2);
        }
        if (DIGITS_PATTERN.matcher(cleaned).matches()) {
            return "H-" + cleaned.replaceFirst("^0+", "");
        }
        return cleaned.replace('_', '-').replaceAll("\\s+", "");
    }

    static String normalizeProvince(String value) {
        if (isPlaceholder(value)) {
            return null;
        }
        String cleaned = collapseWhitespace(value);
        String key = cleaned.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return PROVINCES.getOrDefault(key, titleCase(cleaned));
    }

    static String normalizeSortingCenter(String value) {
        if (isPlaceholder(value)) {
            return null;
        }
        return titleCase(collapseWhitespace(value));
    }

    static Boolean parseBoolean(String value) {
        if (isPlaceholder(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "1", "true", "t", "active", "enabled" -> true;
            case "n", "no", "0", "false", "f", "inactive", "disabled" -> false;
            default -> null;
        };
    }

    private static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String cleaned = collapseWhitespace(value);
        return cleaned.isEmpty() || PLACEHOLDERS.contains(cleaned.toLowerCase(Locale.ROOT));
    }

    private static String collapseWhitespace(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String titleCase(String value) {
        String[] words = value.toLowerCase(Locale.ROOT).split("\\s+");
        List<String> capitalized = new ArrayList<>(words.length);
        for (String word : words) {
            if (!word.isEmpty()) {
                capitalized.add(capitalizeHyphenated(word));
            }
        }
        return String.join(" ", capitalized);
    }

    private static String capitalizeHyphenated(String word) {
        String[] parts = word.split("-", -1);
        List<String> capitalized = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                capitalized.add(part);
            } else {
                capitalized.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
        }
        return String.join("-", capitalized);
    }

    private static int numericId(String hubId) {
        int dash = hubId.lastIndexOf('-');
        String digits = dash >= 0 ? hubId.substring(dash + 1) : hubId;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public record CleanResult(
            List<CleanedHub> hubs,
            int rawRowCount,
            int duplicateRowsRemoved,
            int skippedRows
    ) {
    }

    private record RawRow(String hubId, String province, String sortingCenter, Boolean active) {
    }

    private record ResolvedRow(String hubId, String province, String sortingCenter, Boolean active) {
    }
}
