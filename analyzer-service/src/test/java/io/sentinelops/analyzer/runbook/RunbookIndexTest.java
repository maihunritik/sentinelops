package io.sentinelops.analyzer.runbook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunbookIndexTest {

    private final RunbookIndex index = new RunbookIndex();

    @Test
    void loadsRunbooksFromClasspath() {
        assertThat(index.get("high-error-rate")).isPresent();
        assertThat(index.get("nope")).isEmpty();
    }

    @Test
    void ranksTheMostRelevantRunbookFirst() {
        assertThat(index.search("latency regression p95", 3).getFirst().id()).isEqualTo("latency-regression");
        assertThat(index.search("error rate spike 5xx", 3).getFirst().id()).isEqualTo("high-error-rate");
        assertThat(index.search("consumer lag partition", 3).getFirst().id()).isEqualTo("kafka-consumer-lag");
    }

    @Test
    void serviceSpecificRunbookSurfacesForThatService() {
        assertThat(index.search("payments ERROR RATE SPIKE", 3))
                .extracting(RunbookMatch::id)
                .contains("payments-service", "high-error-rate");
    }

    @Test
    void noMatchesForUnrelatedQuery() {
        assertThat(index.search("zebra giraffe", 3)).isEmpty();
    }
}
