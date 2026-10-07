package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HubCsvReaderTest {

    /**
     * Carries every defect the reader has to tolerate while still reading it as a
     * valid file: a padded header name, a padded and lower-case province, a
     * lower-case hub id, a padded hub id, and three different flag spellings.
     */
    private static final String VALID_CSV = """
            hub_id, Province ,sorting_center,active
            H-500, Gauteng ,Johannesburg Central,Y
            h-501,Western Cape,Cape Town Port,yes
            H-502 ,gauteng,Pretoria North,0
            """;

    // Scenario #1.1 - Read hub records from CSV              (Acceptance 1 and 2)

    /**
     * Given a valid CSV containing hub records, when it is read, then one Hub
     * record is returned per data row and the header row is not returned as a
     * record.
     */
    @Test
    void readReturnsCollectionOfHubRecordsForAValidCsv() {
        List<Hub> hubs = HubCsvReader.read(streamOf(VALID_CSV));

        assertEquals(3, hubs.size());
        assertTrue(hubs.stream().allMatch(hub -> hub.hubId() != null && !hub.hubId().isBlank()));
        assertTrue(hubs.contains(new Hub("h-501", "Western Cape", "Cape Town Port", "yes")));
    }

    /**
     * Each Hub field takes the value from its matching CSV column.
     */
    @Test
    void readMapsEachCsvColumnOntoTheMatchingHubField() {
        List<Hub> hubs = HubCsvReader.read(streamOf(VALID_CSV));

        assertEquals(
                List.of(
                        new Hub("H-500", " Gauteng ", "Johannesburg Central", "Y"),
                        new Hub("h-501", "Western Cape", "Cape Town Port", "yes"),
                        new Hub("H-502 ", "gauteng", "Pretoria North", "0")),
                hubs);
    }



    // Scenario #1.2 - Preserve raw hub values                   (Acceptance 4)

/**
 * Hub values are returned unchanged - original casing, padding and flag
 * spelling preserved.
 */
@Test
void readPreservesMessyHeaderPaddingAndValueCasingBecauseCleaningHappensLater() {
    List<Hub> hubs = HubCsvReader.read(streamOf(VALID_CSV));

    assertEquals(" Gauteng ", hubs.get(0).province());
    assertEquals("h-501", hubs.get(1).hubId());
}



    //Scenario #1.3 - Match columns regardless of header     (Acceptance 3)


    /**
     * Columns are matched by name regardless of header casing.
     */
    @Test
    void readMatchesHeadersRegardlessOfCasing() {
        String reorderedHeaders = "ACTIVE,Sorting_Center,PROVINCE,HUB_ID\n"
                + "Y,Johannesburg Central,Gauteng,H-500\n";

        List<Hub> hubs = HubCsvReader.read(streamOf(reorderedHeaders));

        assertEquals(List.of(new Hub("H-500", "Gauteng", "Johannesburg Central", "Y")), hubs);
    }


    // Scenario #1.4 - Read an empty hub CSV                  (Acceptance 8, Acceptance 9)

    /**
     * A CSV with a header row and no data rows is valid input with an empty
     * answer, not an error. Truncated and freshly created exports are normal.
     */
    @Test
    void readReturnsEmptyListForAHeaderOnlyCsv() {
        String headerOnly = "hub_id, Province ,sorting_center,active\n";

        List<Hub> hubs = HubCsvReader.read(streamOf(headerOnly));

        assertTrue(hubs.isEmpty());
    }

    /**
     * Blank lines, whitespace-only lines and a trailing newline are structural
     * noise, not hub records.
     */
    @Test
    void readIgnoresBlankLinesAndTrailingNewline() {
        String padded = "hub_id,Province,sorting_center,active\n"
                + "\n"
                + "H-500,Gauteng,Johannesburg Central,Y\n"
                + "   \n"
                + "H-501,Western Cape,Cape Town Port,no\n"
                + "\n";

        List<Hub> hubs = HubCsvReader.read(streamOf(padded));

        assertEquals(2, hubs.size());
        assertEquals(List.of("H-500", "H-501"), hubs.stream().map(Hub::hubId).toList());
    }



    // Scenario #1.5 - Reject CSV with a missing required column        (Acceptance 10)
    /**
     * A CSV missing a required column is rejected instead of yielding records
     * with silently empty fields.
     */
    @Test
    void readFailsFastWhenARequiredColumnIsMissing() {
        String missingActive = "hub_id,province,sorting_center\n"
                + "H-500,Gauteng,Johannesburg Central\n";

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> HubCsvReader.read(streamOf(missingActive)));

        assertTrue(thrown.getMessage().contains("active"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("sorting_center"), thrown.getMessage());
    }

    private static InputStream streamOf(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
