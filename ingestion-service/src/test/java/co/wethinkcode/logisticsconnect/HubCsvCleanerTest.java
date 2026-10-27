package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubCsvCleanerTest {

    private static final String HEADER = "hub_id, Province ,sorting_center,active\n";
    private final HubCsvCleaner cleaner = new HubCsvCleaner();

    private static HubRecord find(IngestionReport report, String hubId) {
        return report.hubs().stream()
                .filter(hub -> hub.hubId().equals(hubId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hub " + hubId + " in " + report.hubs()));
    }

    @Test
    void cleansPaddingCasingAndBooleans() {
        IngestionReport report = cleaner.clean(HEADER
                + "H-502 ,gauteng,Pretoria North,0\n"
                + "H-505,Western Cape ,Cape Town  Port,TRUE\n");

        assertEquals(2, report.hubs().size());
        HubRecord h502 = find(report, "H-502");
        assertEquals("Gauteng", h502.province());
        assertEquals("Pretoria North", h502.sortingCenter());
        assertEquals(false, h502.active());

        HubRecord h505 = find(report, "H-505");
        assertEquals("Cape Town Port", h505.sortingCenter());
        assertEquals(true, h505.active());
    }

    @Test
    void dedupesSameCentreKeepingFirstIdAndMajorityFlag() {
        IngestionReport report = cleaner.clean(HEADER
                + "H-500,Gauteng,Johannesburg Central,Y\n"
                + "h-504,Gauteng,Johannesburg Central,true\n"
                + "H-510,Gauteng,johannesburg central,FALSE\n"
                + "H-515,Gauteng,Johannesburg Central,YES\n");

        assertEquals(1, report.hubs().size(), "all four rows describe one facility");
        HubRecord survivor = report.hubs().get(0);
        assertEquals("H-500", survivor.hubId(), "first row in file order survives");
        assertEquals(true, survivor.active(), "Y + true + YES beat a single FALSE");
        assertEquals(3, report.stats().duplicatesMerged());
        assertEquals(4, report.rows().size(), "every source row is still auditable");
        assertEquals(3, report.rows().stream().filter(CleanedRow::duplicate).count());
    }

    @Test
    void nullsPlaceholderValues() {
        IngestionReport report = cleaner.clean(HEADER
                + "H-511,Limpopo,Polokwane Hub,unknown\n"
                + "H-517,Northern Cape,Kimberley Hub,N/A\n");

        assertNull(find(report, "H-511").active());
        assertNull(find(report, "H-517").active());
        assertEquals(2, report.stats().placeholdersReplaced());
    }

    @Test
    void flagsUnrecognizedBooleansAsInvalid() {
        IngestionReport report = cleaner.clean(HEADER
                + "H-599,Gauteng,Sasolburg Depot,maybe\n");

        assertNull(find(report, "H-599").active());
        assertEquals(1, report.stats().invalidValues());
    }

    @Test
    void collapsesKnownNameAliases() {
        IngestionReport report = cleaner.clean(HEADER
                + "H-503,KwaZulu-Natal,Durban Harbour,Y\n"
                + "H-506,Kwa-Zulu Natal,Shelter Point,1\n");

        assertEquals("KwaZulu-Natal", find(report, "H-503").province());
        assertEquals("KwaZulu-Natal", find(report, "H-506").province());
    }

    @Test
    void skipsRowsWithoutAUsableHubId() {
        IngestionReport report = cleaner.clean(HEADER
                + "N/A,Western Cape,Cape Town Port,Y\n"
                + "H-501,Western Cape,Cape Town Port,yes\n");

        assertEquals(1, report.hubs().size());
        assertEquals(2, report.stats().rowsRead());
        assertEquals(1, report.stats().rowsSkipped());
    }

    @Test
    void exposesCleanRecordsForTheRestOfThePipeline() {
        IngestionReport report = cleaner.clean(HEADER
                + "h-501,western cape,Cape Town Port,YES\n");

        assertEquals(1, report.stats().recordsExposed());
        assertTrue(find(report, "H-501").active());
    }
}
