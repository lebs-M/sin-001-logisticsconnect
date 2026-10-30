package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayStageStoreTest {

    @Test
    void returnsStageZeroForUnknownHub() {
        DelayStageStore store = new DelayStageStore();

        StageEvent event = store.get("h-501");

        assertEquals("H-501", event.hubId());
        assertEquals(0, event.stage());
        assertEquals(null, event.timestamp());
    }

    @Test
    void storesUpdatesAndListsStages() {
        DelayStageStore store = new DelayStageStore();

        StageEvent event = store.update("h-501", 5);

        assertEquals("H-501", event.hubId());
        assertEquals(5, event.stage());
        assertNotNull(event.timestamp());
        assertEquals(5, store.get("H-501").stage());
        assertEquals(1, store.all().size());
    }

    @Test
    void rejectsInvalidStage() {
        DelayStageStore store = new DelayStageStore();

        assertThrows(IllegalArgumentException.class, () -> store.update("H-501", -1));
        assertThrows(IllegalArgumentException.class, () -> store.update("H-501", 9));
    }
}
