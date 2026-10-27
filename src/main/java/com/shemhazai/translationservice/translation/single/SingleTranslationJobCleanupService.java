
package com.shemhazai.translationservice.translation.single;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class SingleTranslationJobCleanupService {

    private final SingleTranslationJobRegistry registry;
    private final SingleTranslationPersistenceService persistence;
    private final Path storageRoot;
    private final Duration retention;

    public SingleTranslationJobCleanupService(
            SingleTranslationJobRegistry registry,
            SingleTranslationPersistenceService persistence,
            @Value("${translation.storage.root:/mnt/sugoi-files}")
            String storageRoot,
            @Value("${translation.cleanup.retention:24h}")
            Duration retention
    ) {
        this.registry = registry;
        this.persistence = persistence;
        this.storageRoot = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize();
        this.retention = retention;

        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException(
                    "Cleanup retention must be positive"
            );
        }
    }

    @Scheduled(
            fixedDelayString = "${translation.cleanup.interval:10m}"
    )
    public void cleanupExpiredJobs() {
        Instant cutoff = Instant.now().minus(retention);

        List<SingleTranslationJobEntity> expiredJobs;

        try {
            expiredJobs = persistence.findExpiredTerminalJobs(cutoff);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to query expired translation jobs",
                    exception
            );
            return;
        }

        for (SingleTranslationJobEntity entity : expiredJobs) {
            String jobId = entity.getJobId();

            try {
                // Delete files first. If this fails, retain the DB record
                // so a later cleanup run can retry.
                deleteJobDirectory("uploads", jobId);
                deleteJobDirectory("results", jobId);

                // Recheck age/status before deleting the database row.
                // This protects against stale query results.
                persistence.deleteExpiredJob(jobId, cutoff);

                registry.evict(jobId);

                log.info(
                        "Cleaned expired translation job {}",
                        jobId
                );
            } catch (IOException | RuntimeException exception) {
                log.warn(
                        "Failed to clean translation job {}",
                        jobId,
                        exception
                );
            }
        }
    }

    private void deleteJobDirectory(
            String category,
            String jobId
    ) throws IOException {
        if (!category.equals("uploads")
                && !category.equals("results")) {
            throw new IOException("Invalid cleanup directory");
        }

        // Job IDs are generated internally, but validate anyway.
        if (jobId == null
                || !jobId.matches("[a-zA-Z0-9_-]+")) {
            throw new IOException("Invalid translation job ID");
        }

        Path categoryRoot = storageRoot.resolve(category)
                .toAbsolutePath()
                .normalize();

        Path jobDirectory = categoryRoot.resolve(jobId)
                .normalize();

        if (!jobDirectory.startsWith(categoryRoot)
                || !categoryRoot.equals(jobDirectory.getParent())) {
            throw new IOException(
                    "Refusing to delete path outside job directory"
            );
        }

        if (!Files.exists(jobDirectory)) {
            return;
        }

        try (var paths = Files.walk(jobDirectory)) {
            for (Path path : paths
                    .sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
