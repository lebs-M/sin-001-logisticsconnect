package co.wethinkcode.logisticsconnect;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Normalizes the date formats the legacy exports are known to mix
 * ({@code YYYY-MM-DD}, {@code MM/DD/YYYY}, {@code DD-MM-YYYY}, with one- or
 * two-digit months/days) down to ISO {@code yyyy-MM-dd}.
 *
 * <p>Disambiguation rules, chosen to match the formats documented in
 * {@code ingestion-service/README.md}:
 * <ul>
 *   <li>a 4-digit leading field is always the year;</li>
 *   <li>otherwise {@code /} means month-first ({@code MM/DD/YYYY}) and
 *       {@code -} means day-first ({@code DD-MM-YYYY});</li>
 *   <li>a 4-digit trailing field is the year.</li>
 * </ul>
 *
 * <p>Returns null for blank/placeholder/unparseable values — garbage is never
 * passed through as if it were a date.
 */
public final class DateNormalizer {

    /** True when a CSV header looks like it holds a date. */
    public static boolean looksLikeDateColumn(String header) {
        String squash = header.toLowerCase().replaceAll("[^a-z0-9]", "");
        return squash.contains("date") || squash.contains("updated")
                || squash.contains("created") || squash.contains("opened")
                || squash.contains("timestamp");
    }

    /** @return the value as {@code yyyy-MM-dd}, or null if it is not a valid date. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split("[-/]");
        if (parts.length != 3) {
            return null;
        }
        try {
            int year;
            int month;
            int day;
            if (parts[0].trim().length() == 4) {
                year = Integer.parseInt(parts[0].trim());
                month = Integer.parseInt(parts[1].trim());
                day = Integer.parseInt(parts[2].trim());
            } else if (parts[2].trim().length() <= 4) {
                // year at the end (4- or 2-digit); "/" means month-first, "-" day-first
                boolean monthFirst = value.indexOf('/') >= 0;
                month = Integer.parseInt(parts[monthFirst ? 0 : 1].trim());
                day = Integer.parseInt(parts[monthFirst ? 1 : 0].trim());
                year = Integer.parseInt(parts[2].trim());
            } else {
                return null;
            }
            if (year < 100) {
                year += 2000;
            }
            return LocalDate.of(year, month, day).toString();
        } catch (NumberFormatException | DateTimeException e) {
            return null;
        }
    }

    private DateNormalizer() {
    }
}

