package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the reading side of the pipeline: loading the real bundled
 * {@code hubs-global.csv} through the app's own classpath loader, plus the
 * structural failures {@code HubCsvCleaner} refuses to guess its way around.
 * Field-level cleaning behaviour lives in {@link HubCsvCleanerTest}.
 */
class HubCsvReaderTest {

    private final HubCsvCleaner cleaner = new HubCsvCleaner();

    private static HubRecord find(IngestionReport report, String hubId) {
        return report.hubs().stream()
                .filter(hub -> hub.hubId().equals(hubId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hub " + hubId + " in " + report.hubs()));
    }

    @Test
    void readsTheBundledHubCsvResource() {
        IngestionReport report = cleaner.clean(IngestionServiceApp.loadCsv());

        assertEquals(18, report.stats().rowsRead());
        assertEquals(0, report.stats().rowsSkipped());
        assertEquals(10, report.stats().recordsExposed());
        assertEquals(8, report.stats().duplicatesMerged());
        assertEquals(3, report.stats().placeholdersReplaced());
        assertEquals(0, report.stats().invalidValues());
        assertEquals(18, report.rows().size(), "every source row stays auditable");
        assertEquals(8, report.rows().stream().filter(CleanedRow::duplicate).count());

        HubRecord johannesburg = find(report, "H-500");
        assertEquals("Johannesburg Central", johannesburg.sortingCenter());
        assertEquals("Gauteng", johannesburg.province());
        assertEquals(true, johannesburg.active(), "three true flags beat one FALSE");

        HubRecord pretoria = find(report, "H-502");
        assertEquals("Pretoria North", pretoria.sortingCenter());
        assertEquals("Gauteng", pretoria.province());
        assertEquals(false, pretoria.active(),
                "H-508 has no province so it joins Pretoria North; 0 vs yes ties, first row wins");

        HubRecord durban = find(report, "H-503");
        assertEquals("Durban Harbour", durban.sortingCenter());
        assertEquals("KwaZulu-Natal", durban.province(), "spelling variants collapse onto one spelling");

        assertNull(find(report, "H-511").active(), "'unknown' is a placeholder, not a value");
        assertNull(find(report, "H-517").active(), "'N/A' is a placeholder, not a value");
    }

    @Test
    void rejectsCsvWithoutAnyRows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> cleaner.clean(""));
        assertTrue(e.getMessage().contains("no header row"), e.getMessage());
    }

    @Test
    void rejectsCsvMissingARequiredColumn() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> cleaner.clean("hub_id,province,sorting_center\nH-900,Gauteng,Example Hub\n"));
        assertTrue(e.getMessage().contains("missing required column 'active'"), e.getMessage());
    }

    @Test
    void rejectsMalformedCsv() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> cleaner.clean("hub_id,province,sorting_center,active\n\"H-900,Gauteng,Example Hub,Y\n"));
        assertTrue(e.getMessage().contains("Could not parse"), e.getMessage());
    }

    @Test
    void padsShortRowsWithEmptyCells() {
        IngestionReport report = cleaner.clean("hub_id, Province ,sorting_center,active\nH-901,Gauteng\n");

        assertEquals(1, report.hubs().size());
        HubRecord hub = find(report, "H-901");
        assertEquals("Gauteng", hub.province());
        assertNull(hub.sortingCenter());
        assertNull(hub.active());
    }

    @Test
    void skipsBlankLinesWithoutCountingThemAsRead() {
        IngestionReport report = cleaner.clean("hub_id, Province ,sorting_center,active\n"
                + "H-901,Gauteng,Example Hub,Y\n"
                + " , , ,\n"
                + "H-902,Limpopo,Polokwane Hub,N\n");

        assertEquals(2, report.hubs().size());
        assertEquals(2, report.stats().rowsRead());
    }
}
