package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertServiceTest {

    @Test
    void ignoresStagesBelowThreshold() {
        AlertService service = new AlertService(4);

        Alert alert = service.handle(new StageEvent("H-501", 3, "2026-07-18T10:00:00Z"));

        assertNull(alert);
        assertTrue(service.all().isEmpty());
    }

    @Test
    void postsSimulatedAlertAtOrAboveThreshold() {
        AlertService service = new AlertService(4);

        Alert alert = service.handle(new StageEvent("H-501", 4, "2026-07-18T10:00:00Z"));

        assertEquals("H-501", alert.hubId());
        assertEquals(4, alert.stage());
        assertEquals("posted", alert.status());
        assertEquals("simulated-social-media", alert.channel());
        assertEquals(1, service.all().size());
    }
}
