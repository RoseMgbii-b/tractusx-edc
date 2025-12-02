package org.eclipse.tractusx.edc.audit.mapping.extractor;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReflectionExtractorTest {

    private ReflectionExtractor extractor;
    private Monitor monitor;

    static class EventWithGetter {
        private final String contractNegotiationId = "cn-1";
        public String getContractNegotiationId() { return contractNegotiationId; }
    }

    static class EventWithBooleanGetter {
        private final boolean active = true;
        public boolean isActive() { return active; }
    }

    static class EventWithField {
        private final String counterPartyId = "provider-x";
    }

    static class EventWithLooseField {
        private final String contract_negotiation_id = "loose-22";
    }

    static class Nested {
        private final String contractNegotiationId = "nested-33";
        public String getContractNegotiationId() { return contractNegotiationId; }
    }
    static class EventWithNested {
        public Nested getContract() { return new Nested(); }
    }

    @BeforeEach
    void setup() {
        monitor = mock(Monitor.class);
        extractor = new ReflectionExtractor(monitor);
    }

    @Test
    void extract_shouldExtractViaGetter() {
        var result = extractor.extractStringProperty(new EventWithGetter(), "contractNegotiationId");
        assertThat(result).isEqualTo("cn-1");
    }

    @Test
    void extract_shouldExtractViaBooleanGetter() {
        var result = extractor.extractStringProperty(new EventWithBooleanGetter(), "active");
        assertThat(result).isEqualTo("true");
    }

    @Test
    void extract_shouldExtractViaFieldAccess() {
        var result = extractor.extractStringProperty(new EventWithField(), "counterPartyId");
        assertThat(result).isEqualTo("provider-x");
    }

    @Test
    void extract_shouldExtractViaLooseMatching() {
        var result = extractor.extractStringProperty(new EventWithLooseField(), "contractNegotiationId");
        assertThat(result).isEqualTo("loose-22");
    }

    @Test
    void extract_shouldExtractFromNestedObject() {
        var result = extractor.extractStringProperty(new EventWithNested(), "contractNegotiationId");
        assertThat(result).isEqualTo("nested-33");
    }

    @Test
    void extract_shouldReturnNullIfNotFound() {
        var result = extractor.extractStringProperty(new Object(), "unknown");
        assertThat(result).isNull();
    }
}
