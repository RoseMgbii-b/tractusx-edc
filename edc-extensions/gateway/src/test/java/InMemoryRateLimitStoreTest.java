import org.eclipse.tractusx.gateway.InMemoryRateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimitStoreTest {

    private InMemoryRateLimitStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryRateLimitStore();
    }

    @Test
    void checkRateLimit_whenUnderLimit_shouldReturnTrue() {
        store.recordRequest("client");

        var result = store.checkRateLimit("client", 5L, 60L);

        assertThat(result).isTrue();
    }

    @Test
    void checkRateLimit_whenLimitExceeded_shouldReturnFalse() {
        store.recordRequest("client");
        store.recordRequest("client");

        var result = store.checkRateLimit("client", 1L, 60L);

        assertThat(result).isFalse();
    }

    @Test
    void getRequestCount_shouldDropExpiredEntries() throws InterruptedException {
        store.recordRequest("client");

        Thread.sleep(1100);

        assertThat(store.getRequestCount("client", 1)).isZero();
    }
}
