package com.shemhazai.translationservice.translation.single;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class SingleTranslationJobRegistry {

    private final ConcurrentMap<String, SingleTranslationJob> jobs =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<String, CompletableFuture<SingleTranslationResult>> pendingResults =
            new ConcurrentHashMap<>();

    private final SingleTranslationPersistenceService persistence;

    public SingleTranslationJobRegistry(SingleTranslationPersistenceService persistence) {
        this.persistence = persistence;
    }

    /**
     * Registers the job and its future before sending a command to Sugoi.
     */
    public CompletableFuture<SingleTranslationResult> register(
            SingleTranslationJob job
    ) {
        String jobId = job.getJobId();

        SingleTranslationJob existing = jobs.putIfAbsent(jobId, job);

        if (existing != null) {
            throw new IllegalStateException(
                    "Translation job already exists: " + jobId
            );
        }

        CompletableFuture<SingleTranslationResult> future =
                new CompletableFuture<>();

        pendingResults.put(jobId, future);

        try {
            persistence.save(job);
        } catch (RuntimeException exception) {
            pendingResults.remove(jobId, future);
            jobs.remove(jobId, job);
            throw exception;
        }

        return future;
    }

    public SingleTranslationJob getJob(String jobId) {
        SingleTranslationJob cached = jobs.get(jobId);
        if (cached != null) {
            return cached;
        }

        return persistence.findEntity(jobId)
                .map(persistence::restoreJob)
                .map(job -> {
                    SingleTranslationJob existing =
                            jobs.putIfAbsent(jobId, job);


                    return existing == null ? job : existing;
                })
                .orElse(null);
    }

    public CompletableFuture<SingleTranslationResult> getPendingResult(
            String jobId
    ) {
        return pendingResults.get(jobId);
    }


    public boolean updateProgress(
            String jobId,
            String stage,
            int currentStep,
            int totalSteps,
            String detail
    ) {
        SingleTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (isTerminal(job.getStatus())) {
                return false;
            }

            job.updateProgress(
                    stage,
                    currentStep,
                    totalSteps,
                    detail
            );

            persistence.save(job);

            return true;
        }
    }


    /**
     * Called when Sugoi returns manga_translation_result.
     */

    public boolean complete(
            String jobId,
            SingleTranslationResult result
    ) {
        SingleTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (isTerminal(job.getStatus())) {
                return false;
            }

            CompletableFuture<SingleTranslationResult> future =
                    pendingResults.get(jobId);

            try {
                SingleTranslationResult storedResult =
                        persistence.storeResult(job, result);

                job.complete(storedResult);
                persistence.save(job);

                if (future != null) {
                    pendingResults.remove(jobId, future);
                    future.complete(storedResult);
                }

                return true;
            } catch (Exception exception) {
                String error = "Failed to persist translation result: "
                        + exception.getMessage();

                job.fail(error);

                try {
                    persistence.save(job);
                } catch (RuntimeException saveException) {
                    // Preserve the original failure.
                }

                if (future != null) {
                    pendingResults.remove(jobId, future);
                    future.completeExceptionally(
                            new TranslationJobException(
                                    jobId,
                                    error
                            )
                    );
                }

                return false;
            }
        }
    }


    /**
     * Called when submission or translation fails.
     */
    public boolean fail(String jobId, String message) {
        SingleTranslationJob job = jobs.get(jobId);

        if (job == null) {
            return false;
        }

        CompletableFuture<SingleTranslationResult> future =
                pendingResults.get(jobId);

        synchronized (job) {
            if (isTerminal(job.getStatus())) {
                return false;
            }

            job.fail(message);
            persistence.save(job);

            if (future != null) {
                pendingResults.remove(jobId, future);
                future.completeExceptionally(
                        new TranslationJobException(jobId, message)
                );
            }
        }

        return true;
    }

    private boolean isTerminal(SingleTranslationJobStatus status) {
        return status == SingleTranslationJobStatus.COMPLETED
                || status == SingleTranslationJobStatus.FAILED;
    }

    public static class TranslationJobException
            extends RuntimeException {

        private final String jobId;

        public TranslationJobException(
                String jobId,
                String message
        ) {
            super(message);
            this.jobId = jobId;
        }

        public String getJobId() {
            return jobId;
        }
    }


    public List<SingleTranslationJob> findExpiredTerminalJobs(
            Instant cutoff
    ) {
        List<SingleTranslationJob> expired = new ArrayList<>();

        for (SingleTranslationJob job : jobs.values()) {
            synchronized (job) {
                if (isTerminal(job.getStatus())
                        && job.getUpdatedAt() != null
                        && job.getUpdatedAt().isBefore(cutoff)) {
                    expired.add(job);
                }
            }
        }

        return expired;
    }

    public boolean removeIfExpired(
            SingleTranslationJob job,
            Instant cutoff
    ) {
        synchronized (job) {
            if (!isTerminal(job.getStatus())
                    || job.getUpdatedAt() == null
                    || !job.getUpdatedAt().isBefore(cutoff)) {
                return false;
            }

            boolean removed = jobs.remove(job.getJobId(), job);

            if (removed) {
                pendingResults.remove(job.getJobId());
            }

            return removed;
        }
    }

    public void evict(String jobId) {
        jobs.remove(jobId);
        pendingResults.remove(jobId);
    }

    public boolean markSubmitted(String jobId) {
        SingleTranslationJob job = getJob(jobId);

        if (job == null) {
            return false;
        }

        synchronized (job) {
            if (job.getStatus() != SingleTranslationJobStatus.QUEUED) {
                return false;
            }

            job.updateStatus(SingleTranslationJobStatus.SUBMITTED);
            persistence.save(job);
            return true;
        }
    }
}
