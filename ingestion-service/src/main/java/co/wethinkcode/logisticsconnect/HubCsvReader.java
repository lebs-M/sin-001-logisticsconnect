package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads hub records out of the CSV shape the export produces.
 */
public final class HubCsvReader {

    /** Header names the export must supply for a row to be mapped at all. */
    private static final List<String> REQUIRED_COLUMNS =
            List.of("hub_id", "province", "sorting_center", "active");

    /** Classpath location of the export the service ships with. */
    private static final String BUNDLED_CSV = "/hubs-global.csv";

    private HubCsvReader() {
    }

    /**
     * Reads hub records from the bundled {@code hubs-global.csv}.
     * @throws IllegalStateException    if the bundled export is not on the classpath
     * @throws UncheckedIOException     if the stream cannot be read
     * @throws IllegalArgumentException if the CSV is structurally malformed
     */
    public static List<Hub> read() {
        InputStream in = HubCsvReader.class.getResourceAsStream(BUNDLED_CSV);
        if (in == null) {
            throw new IllegalStateException("Bundled hub CSV not found on the classpath: " + BUNDLED_CSV);
        }
        return read(in);
    }

    /**
     * Reads hub records from the given CSV file.
     * @throws UncheckedIOException     if the file cannot be opened or read
     * @throws IllegalArgumentException if the CSV is structurally malformed
     */
    public static List<Hub> read(Path file) {
        try {
            return read(Files.newInputStream(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read hub CSV file " + file, e);
        }
    }

    /**
     * Reads hub records from the given CSV stream.
     * @throws UncheckedIOException  if the stream cannot be read
     * @throws IllegalArgumentException if the CSV is structurally malformed
     */
    public static List<Hub> read(InputStream csv) {
        try (CSVReader reader = new CSVReaderBuilder(
                new InputStreamReader(csv, StandardCharsets.UTF_8)).build()) {
            List<Hub> hubs = new ArrayList<>();

            String[] header = reader.readNext();
            if (header == null) {
                return List.of();
            }

            Map<String, Integer> columns = indexColumns(header);
            requireColumns(columns, header);

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (isBlank(row)) {
                    continue;
                }
                hubs.add(new Hub(
                        named(row, columns, "hub_id"),
                        named(row, columns, "province"),
                        named(row, columns, "sorting_center"),
                        named(row, columns, "active")));
            }
            return hubs;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the hub CSV stream", e);
        } catch (CsvValidationException e) {
            throw new IllegalArgumentException("Malformed hub CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Maps each header name onto its column index.
     */
    private static Map<String, Integer> indexColumns(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            columns.put(header[i].trim().toLowerCase(), i);
        }
        return columns;
    }

    private static String named(String[] row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        return index == null || index >= row.length ? "" : row[index];
    }

    /**
     * Fails fast when a header the reader needs is absent.
     */
    private static void requireColumns(Map<String, Integer> columns, String[] header) {
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                throw new IllegalArgumentException(
                        "Hub CSV is missing the required column \"" + required
                                + "\". Headers found: " + String.join(", ", header));
            }
        }
    }

    /**
     * A row is blank when every cell it holds is blank.
     */
    private static boolean isBlank(String[] row) {
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
