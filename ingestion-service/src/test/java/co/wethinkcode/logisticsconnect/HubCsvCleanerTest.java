package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubCsvCleanerTest {

    private final HubCsvCleaner cleaner = new HubCsvCleaner();

    @Test
    void cleansCasingPaddingBooleansAndMissingProvince() throws Exception {
        String csv = """
                hub_id, Province ,sorting_center,active
                H-502 ,gauteng,Pretoria North,0
                H-505,Western Cape ,Cape Town  Port,TRUE
                H-506,Kwa-Zulu Natal,Durban Harbour,1
                """;

        HubCsvCleaner.CleanResult result = cleaner.clean(new StringReader(csv));

        assertEquals(3, result.hubs().size());
        assertEquals("Gauteng", find(result, "H-502").province());
        assertEquals("Cape Town Port", find(result, "H-505").sortingCenter());
        assertTrue(find(result, "H-505").active());
        assertEquals("KwaZulu-Natal", find(result, "H-506").province());
    }

    @Test
    void resolvesDuplicatesAndInfersMissingProvince() throws Exception {
        String csv = """
                hub_id, Province ,sorting_center,active
                H-502 ,gauteng,Pretoria North,0
                H-508,,Pretoria North,yes
                H-500, Gauteng ,Johannesburg Central,Y
                h-504,Gauteng,johannesburg central,FALSE
                H-510,Gauteng,Johannesburg Central,YES
                H-513,North West,Rustenburg Hub,unknown
                """;

        HubCsvCleaner.CleanResult result = cleaner.clean(new StringReader(csv));

        assertEquals(6, result.rawRowCount());
        assertEquals(3, result.hubs().size());
        assertEquals(3, result.duplicateRowsRemoved());

        CleanedHub pretoria = find(result, "H-502");
        assertEquals("Gauteng", pretoria.province());
        assertFalse(pretoria.active());
        assertEquals(List.of("H-502", "H-508"), pretoria.aliases());

        CleanedHub johannesburg = find(result, "H-500");
        assertTrue(johannesburg.active());
        assertEquals(List.of("H-500", "H-504", "H-510"), johannesburg.aliases());

        assertNull(find(result, "H-513").active());
    }

    @Test
    void handlesTheRealLegacyExport() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/hubs-global.csv")) {
            assertTrue(input != null);
            HubCsvCleaner.CleanResult result = cleaner.clean(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );

            assertEquals(18, result.rawRowCount());
            assertEquals(10, result.hubs().size());

            CleanedHub johannesburg = find(result, "H-500");
            assertEquals("Johannesburg Central", johannesburg.sortingCenter());
            assertEquals(List.of("H-500", "H-504", "H-510", "H-515"), johannesburg.aliases());

            CleanedHub durban = find(result, "H-503");
            assertEquals("KwaZulu-Natal", durban.province());
            assertEquals(List.of("H-503", "H-506", "H-516"), durban.aliases());

            CleanedHub pretoria = find(result, "H-502");
            assertEquals("Gauteng", pretoria.province());
        }
    }

    @Test
    void normalizesIdentifiersAndPlaceholders() {
        assertEquals("H-501", HubCsvCleaner.normalizeHubId(" h-501 "));
        assertEquals("H-501", HubCsvCleaner.normalizeHubId("H 0501"));
        assertEquals("H-501", HubCsvCleaner.normalizeHubId("501"));
        assertNull(HubCsvCleaner.normalizeHubId("N/A"));
        assertNull(HubCsvCleaner.parseBoolean("unknown"));
        assertTrue(HubCsvCleaner.parseBoolean("YES"));
        assertFalse(HubCsvCleaner.parseBoolean("0"));
        assertEquals("KwaZulu-Natal", HubCsvCleaner.normalizeProvince("kwazulu natal"));
        assertEquals("KwaZulu-Natal", HubCsvCleaner.normalizeProvince("Kwa-Zulu Natal"));
    }

    private static CleanedHub find(HubCsvCleaner.CleanResult result, String id) {
        return result.hubs().stream()
                .filter(hub -> hub.hubId().equals(id))
                .findFirst()
                .orElseThrow();
    }


    @Test
    void returnsAnEmptyResultForEmptyCsv() throws Exception {
        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(""));

        assertTrue(result.hubs().isEmpty());
        assertEquals(0, result.rawRowCount());
        assertEquals(0, result.duplicateRowsRemoved());
        assertEquals(0, result.skippedRows());
    }


    @Test
    void returnsAnEmptyResultForHeaderOnlyCsv() throws Exception {
        String csv = "hub_id,province,sorting_center,active\n";

        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(csv));

        assertTrue(result.hubs().isEmpty());
        assertEquals(0, result.rawRowCount());
        assertEquals(0, result.duplicateRowsRemoved());
        assertEquals(0, result.skippedRows());
    }


    @Test
    void skipsRowsWithMissingOrPlaceholderHubIds() throws Exception {
        String csv = """
            hub_id,province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,true
            ,Gauteng,Pretoria North,true
            N/A,Western Cape,Cape Town Port,true
            """;

        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(csv));

        assertEquals(3, result.rawRowCount());
        assertEquals(1, result.hubs().size());
        assertEquals(2, result.skippedRows());
        assertEquals(0, result.duplicateRowsRemoved());
        assertEquals("H-500", result.hubs().get(0).hubId());
    }


    @Test
    void supportsReorderedAndMixedCaseHeaders() throws Exception {
        String csv = """
            ACTIVE,SORTING_CENTER,PROVINCE,HUB_ID
            true,Durban Harbour,KwaZulu-Natal,H-520
            """;

        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(csv));

        assertEquals(1, result.hubs().size());

        CleanedHub hub = find(result, "H-520");
        assertEquals("KwaZulu-Natal", hub.province());
        assertEquals("Durban Harbour", hub.sortingCenter());
        assertTrue(hub.active());
    }


    @Test
    void resolvesTiedActiveValuesUsingCanonicalRow() throws Exception {
        String csv = """
            hub_id,province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,true
            H-501,Gauteng,Johannesburg Central,false
            """;

        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(csv));

        assertEquals(1, result.hubs().size());

        CleanedHub hub = find(result, "H-500");
        assertEquals("H-500", hub.hubId());
        assertTrue(hub.active());
        assertEquals(List.of("H-500", "H-501"), hub.aliases());
        assertEquals(1, result.duplicateRowsRemoved());
    }


    @Test
    void doesNotMergeDifferentSortingCentersOrProvinces()
            throws Exception {
        String csv = """
            hub_id,province,sorting_center,active
            H-500,Gauteng,Johannesburg Central,true
            H-501,Gauteng,Pretoria North,true
            H-502,Western Cape,Johannesburg Central,true
            """;

        HubCsvCleaner.CleanResult result =
                cleaner.clean(new StringReader(csv));

        assertEquals(3, result.hubs().size());
        assertEquals(0, result.duplicateRowsRemoved());
    }


}
