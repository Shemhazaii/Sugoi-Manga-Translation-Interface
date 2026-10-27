package com.shemhazai.translationservice.translation.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BatchTranslationJobRegistry {

    private final BatchTranslationPersistenceService persistence;

    private final Map<String, BatchTranslationJob> jobs =
            new ConcurrentHashMap<>();

    public BatchTranslationJobRegistry(BatchTranslationPersistenceService persistence) {
        this.persistence = persistence;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        restoreQueuedJobs();
        recoverInterruptedJobs();
    }

    public void register(BatchTranslationJob job) {
        BatchTranslationJob existing = jobs.putIfAbsent(
                job.getJobId(),
                job
        );

        if (existing != null) {
            throw new IllegalStateException(
                    "Batch translation job already exists: "
                            + job.getJobId()
            );
        }

        try {
            persistence.save(job);
        } catch (RuntimeException e) {
            jobs.remove(job.getJobId(), job);
            throw e;
        }
    }

    public BatchTranslationJob getJob(String jobId) {
        return jobs.get(jobId);
    }


    public boolean packagingCompleted(
            String jobId,
            java.nio.file.Path resultArchive
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (job.getStatus() != BatchTranslationStatus.PACKAGING) {
                return false;
            }

            if (resultArchive == null
                    || !java.nio.file.Files.isRegularFile(resultArchive)) {
                throw new IllegalStateException(
                        "Result archive does not exist: " + resultArchive
                );
            }

            job.setResultArchive(resultArchive);
            job.updateStatus(BatchTranslationStatus.COMPLETED);
            persistence.save(job);

            return true;
        }
    }


    public boolean markSubmitted(String jobId) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (job.getStatus() != BatchTranslationStatus.QUEUED) {
                return false;
            }

            job.updateStatus(BatchTranslationStatus.SUBMITTED);
            persistence.save(job);
            return true;
        }
    }

    public void restoreQueuedJobs() {
        var queuedJobs = persistence.findByStatuses(
                java.util.List.of(BatchTranslationStatus.QUEUED)
        );

        for (var entity : queuedJobs) {
            BatchTranslationJob job = persistence.restoreJob(entity);

            jobs.putIfAbsent(job.getJobId(), job);
        }

        log.info("Restored {} queued batch translation jobs", queuedJobs.size());
    }

    public void recoverInterruptedJobs() {
        var interrupted = persistence.findByStatuses(
                java.util.List.of(
                        BatchTranslationStatus.SUBMITTED,
                        BatchTranslationStatus.TRANSLATING,
                        BatchTranslationStatus.PACKAGING
                )
        );

        int failed = 0;

        for (var entity : interrupted) {
            if (entity.getStatus() == BatchTranslationStatus.PACKAGING) {
                entity.setStatus(BatchTranslationStatus.FAILED);
                entity.setErrorMessage(
                        "Packaging interrupted by application restart; retry is not yet supported"
                );
            } else {
                entity.setStatus(BatchTranslationStatus.FAILED);
                entity.setErrorMessage(
                        "Batch translation interrupted by application restart"
                );
            }

            persistence.save(entity);
            failed++;
        }

        log.info("Marked {} interrupted batch jobs as FAILED", failed);
    }

    public void imagesFound(
            String jobId,
            int totalImages
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return;
        }

        job.imagesFound(totalImages);
        persistence.save(job);
    }

    public void imageStarted(
            String jobId,
            String imagePath,
            int imageIndex,
            int totalImages
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return;
        }

        job.imageStarted(
                imagePath,
                imageIndex,
                totalImages
        );
        persistence.save(job);
    }

    public void imageProgress(
            String jobId,
            String imagePath,
            int current,
            int total,
            String stage,
            String detail,
            int imageIndex,
            int totalImages
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return;
        }

        job.imageProgress(
                imagePath,
                current,
                total,
                stage,
                detail,
                imageIndex,
                totalImages
        );
        persistence.save(job);
    }

    public void imageCompleted(
            String jobId,
            String imagePath,
            int imageIndex,
            int totalImages
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return;
        }

        job.imageCompleted(
                imagePath,
                imageIndex,
                totalImages
        );

        persistence.save(job);
    }

    public boolean complete(
            String jobId,
            int total,
            int succeeded,
            int failed
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (isTerminal(job.getStatus())) {
                return false;
            }

            job.complete(
                    total,
                    succeeded,
                    failed
            );
            persistence.save(job);

            return true;
        }
    }

    public boolean fail(
            String jobId,
            String message
    ) {
        BatchTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (isTerminal(job.getStatus())) {
                return false;
            }

            job.fail(message);
            persistence.save(job);
            return true;
        }
    }

    private boolean isTerminal(
            BatchTranslationStatus status
    ) {
        return status == BatchTranslationStatus.COMPLETED
                || status == BatchTranslationStatus.FAILED;
    }
}