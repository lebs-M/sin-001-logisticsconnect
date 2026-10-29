package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HubRepositoryTest {

    @Test
    void normalizesHubLookupKeys() {
        assertEquals("H-501", HubRepository.normalizeKey(" h-501 "));
        assertEquals("H-501", HubRepository.normalizeKey("501"));
        assertEquals("H-501", HubRepository.normalizeKey("h-0501"));
        assertEquals("", HubRepository.normalizeKey(null));
    }
}
