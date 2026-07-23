package com.jobsrch.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JobImportAuditServiceTests {

    @Autowired
    private JobImportAuditService audit;

    @Autowired
    private JobImportStatusService status;

    @Autowired
    private JobImportAttemptRepository attempts;

    @Autowired
    private JobImportBatchRepository batches;

    @AfterEach
    void cleanUp() {
        attempts.deleteAll();
        batches.deleteAll();
    }

    @Test
    void exposesPartialFailureWithoutLeakingRawExceptionDetails() {
        UUID batchId = audit.startBatch();
        audit.success(
                batchId,
                JobProvider.GREENHOUSE,
                ImportSourceType.COMPANY_BOARD,
                "example",
                "Example",
                Instant.now(),
                12);
        audit.failure(
                batchId,
                JobProvider.LEVER,
                ImportSourceType.COMPANY_BOARD,
                "unavailable",
                "Unavailable Board",
                Instant.now());
        audit.complete(batchId, 12, 3, 1);

        ImportStatusResponse result = status.status();

        assertThat(result.recentBatches()).singleElement().satisfies(batch -> {
            assertThat(batch.status()).isEqualTo(ImportBatchStatus.PARTIAL_FAILURE);
            assertThat(batch.jobsReceived()).isEqualTo(12);
            assertThat(batch.jobsExpired()).isEqualTo(3);
            assertThat(batch.failureCount()).isEqualTo(1);
            assertThat(batch.attempts()).hasSize(2);
            assertThat(batch.attempts().get(1).errorMessage())
                    .isEqualTo("Provider request failed");
        });
    }
}
