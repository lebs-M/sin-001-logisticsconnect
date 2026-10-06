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
import java.util.List;

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

            if (reader.readNext() == null) {
                return List.of();
            }

            String[] row;
            while ((row = reader.readNext()) != null) {
                hubs.add(new Hub(cell(row, 0), cell(row, 1), cell(row, 2), cell(row, 3)));
            }
            return hubs;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the hub CSV stream", e);
        } catch (CsvValidationException e) {
            throw new IllegalArgumentException("Malformed hub CSV: " + e.getMessage(), e);
        }
    }

    private static String cell(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }
}

