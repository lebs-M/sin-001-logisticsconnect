package co.wethinkcode.logisticsconnect;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads hub records out of the CSV shape the export produces.
 */
public final class HubCsvReader {

    private HubCsvReader() {
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
