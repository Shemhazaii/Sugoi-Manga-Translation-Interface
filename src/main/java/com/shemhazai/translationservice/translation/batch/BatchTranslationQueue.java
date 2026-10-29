
package com.shemhazai.translationservice.translation.batch;

import com.shemhazai.translationservice.sugoi.SugoiCommandService;
import com.shemhazai.translationservice.sugoi.SugoiModuleStatusEvent;
import com.shemhazai.translationservice.sugoi.SugoiSlotReleasedEvent;
import com.shemhazai.translationservice.sugoi.SugoiTranslationCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class BatchTranslationQueue {

    private final BatchTranslationPersistenceService persistence;
    private final BatchTranslationJobRegistry registry;
    private final SugoiTranslationCoordinator coordinator;
    private final SugoiCommandService commandService;
    private final BatchTranslationPackagingService packagingService;
    private final Duration timeout;

    private String activeJobId;
    private boolean dispatching;
    private boolean terminalReceived;
    private boolean timedOut;
    private Instant activeStartedAt;

    public BatchTranslationQueue(
            BatchTranslationPersistenceService persistence,
            BatchTranslationJobRegistry registry,
            SugoiTranslationCoordinator coordinator,
            SugoiCommandService commandService,
            BatchTranslationPackagingService packagingService,
            @Value("${translation.batch.timeout:30m}") Duration timeout
    ) {
        this.persistence = persistence;
        this.registry = registry;
        this.coordinator = coordinator;
        this.commandService = commandService;
        this.packagingService = packagingService;
        this.timeout = timeout;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void onApplicationReady() {
        log.info("Batch translation queue initialized");
        tryDispatch();
    }

    @Scheduled(fixedDelayString = "${translation.queue.poll-interval:1000}")
    public synchronized void poll() {
        checkTimeout();
        tryDispatch();
    }

    @EventListener
    public void onModuleStatusUpdated(SugoiModuleStatusEvent event) {
        coordinator.onModuleStatusUpdated();
    }

    @EventListener
    public synchronized void onBatchTerminal(
            BatchTranslationTerminalEvent event
    ) {
        if (!event.jobId().equals(activeJobId)) {
            log.warn(
                    "Batch terminal event does not match active job: {}",
                    event.jobId()
            );
            return;
        }

        terminalReceived = true;
        coordinator.markTerminal(event.jobId());

        BatchTranslationJob job = registry.getJob(event.jobId());
        if (job == null) {
            log.error("Batch job not found after terminal event: {}", event.jobId());
            return;
        }

        try {
            packagingService.packageResult(job);
        } catch (Exception e) {
            log.error("Failed to package batch result for {}", event.jobId(), e);
            registry.fail(
                    event.jobId(),
                    "Failed to package translated results: " + rootMessage(e)
            );
        }
    }

    @EventListener
    public synchronized void onSugoiSlotReleased(
            SugoiSlotReleasedEvent event
    ) {
        if (!"batch".equals(event.jobType())
                || !event.jobId().equals(activeJobId)) {
            return;
        }

        log.info("Batch job released Sugoi slot: {}", event.jobId());

        activeJobId = null;
        dispatching = false;
        terminalReceived = false;
        timedOut = false;
        activeStartedAt = null;

        tryDispatch();
    }

    private void checkTimeout() {
        if (activeJobId == null
                || activeStartedAt == null
                || timedOut
                || terminalReceived) {
            return;
        }

        Duration elapsed = Duration.between(activeStartedAt, Instant.now());
        if (elapsed.compareTo(timeout) < 0) {
            return;
        }

        timedOut = true;
        String jobId = activeJobId;

        registry.fail(jobId, "Batch translation timed out after " + timeout);
        coordinator.markTimedOut(jobId);

        log.error("Batch translation timed out: jobId={}, elapsed={}", jobId, elapsed);
    }

    private synchronized void tryDispatch() {
        if (dispatching || activeJobId != null || coordinator.isOccupied()) {
            return;
        }

        var entity = persistence.findNextQueuedJob().orElse(null);
        if (entity == null) {
            return;
        }

        BatchTranslationJob job = registry.getJob(entity.getJobId());
        if (job == null
                || job.getStatus() != BatchTranslationStatus.QUEUED
                || job.getInputFolder() == null
                || job.getOutputFolder() == null) {
            log.warn("Skipping invalid queued batch job {}", entity.getJobId());
            return;
        }

        if (!coordinator.tryAcquire(job.getJobId(), "batch")) {
            return;
        }

        if (!registry.markSubmitted(job.getJobId())) {
            coordinator.cancelAcquire(job.getJobId());
            return;
        }

        if (!Objects.equals(job.getSourceLang(), "ja") || !Objects.equals(job.getTargetLang(), "en")) {
            try {
                commandService.sendAndWait(commandService.loadMangaModule(job.getSourceLang(),job.getTargetLang()),Duration.ofSeconds(180));
            }catch (Exception e){
                dispatching = false;
                timedOut = true;

                registry.fail(
                        job.getJobId(),
                        "Failed to submit translation command: " + rootMessage(e)
                );

                coordinator.markTimedOut(job.getJobId());

                log.error(
                        "Failed to submit translation command for job {}",
                        job.getJobId(),
                        e
                );
                return;
            }
        }

        activeJobId = job.getJobId();
        dispatching = true;
        terminalReceived = false;
        timedOut = false;
        activeStartedAt = Instant.now();

        String folderPath = toSugoiPath(job.getInputFolder(), job.getJobId(), "uploads");
        String outputFolder = toSugoiPath(job.getOutputFolder(), job.getJobId(), "results");

        log.info(
                "Dispatching batch translation: jobId={}, folder={}, output={}",
                activeJobId, folderPath, outputFolder
        );

        CompletableFuture<Void> future;

        try {
            future = commandService.translateMangaFolder(
                    job.getJobId(),
                    folderPath,
                    job.getSourceLang(),
                    job.getTargetLang(),
                    job.getTranslator(),
                    outputFolder
            );
        } catch (RuntimeException e) {
            dispatching = false;
            timedOut = true;
            registry.fail(
                    job.getJobId(),
                    "Failed to submit batch command: " + rootMessage(e)
            );
            coordinator.markTimedOut(job.getJobId());
            log.error("Failed to submit batch command for {}", job.getJobId(), e);
            return;
        }

        String jobId = job.getJobId();

        future.whenComplete((ignored, error) -> {
            synchronized (BatchTranslationQueue.this) {
                dispatching = false;

                if (!jobId.equals(activeJobId)) {
                    return;
                }

                if (error != null) {
                    timedOut = true;
                    registry.fail(
                            jobId,
                            "Failed to submit batch command: " + rootMessage(error)
                    );
                    coordinator.markTimedOut(jobId);
                    log.error("Failed to send batch command for {}", jobId, error);
                    return;
                }

                log.info("Batch command sent to Sugoi: {}", jobId);
            }
        });
    }

    private String toSugoiPath(Path path, String jobId, String expectedRoot) {
        Path normalized = path.toAbsolutePath().normalize();
        String value = normalized.toString().replace('\\', '/');

        int filesIndex = value.lastIndexOf("/files/");
        if (filesIndex >= 0) {
            return value.substring(filesIndex + 1);
        }

        // Storage root /mnt/sugoi-files maps to Sugoi's files/ root.
        return "files/" + expectedRoot + "/" + jobId
                + (expectedRoot.equals("uploads") ? "/extracted" : "/translated");
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
