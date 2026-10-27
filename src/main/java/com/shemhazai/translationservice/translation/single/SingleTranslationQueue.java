
package com.shemhazai.translationservice.translation.single;

import com.shemhazai.translationservice.sugoi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SingleTranslationQueue {

    private final SugoiStateService stateService;
    private final SingleTranslationPersistenceService persistence;
    private final SugoiTranslationCoordinator coordinator;
    private final SingleTranslationJobRegistry registry;
    private final SugoiCommandService commandService;
    private final Duration timeout;

    private String activeJobId;
    private boolean terminalReceived;
    private boolean dispatching;
    private Instant activeStartedAt;
    private boolean activeTimedOut;

    public SingleTranslationQueue(
            SugoiStateService stateService,
            SingleTranslationPersistenceService persistence, SugoiTranslationCoordinator coordinator,
            SingleTranslationJobRegistry registry,
            SugoiCommandService commandService,
            @Value("${translation.single.timeout:5m}") Duration timeout
    ) {
        this.stateService = stateService;
        this.persistence = persistence;
        this.coordinator = coordinator;
        this.registry = registry;
        this.commandService = commandService;
        this.timeout = timeout;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Translation queue initialized");
        tryDispatch();
    }

    /**
     * Safety net: queued jobs are reconsidered periodically.
     */
    @Scheduled(fixedDelayString = "${translation.queue.poll-interval:1000}")
    public synchronized void poll() {
        checkActiveTimeout();
        tryDispatch();
    }


    @EventListener
    public void onModuleStatusUpdated(SugoiModuleStatusEvent event) {
        coordinator.onModuleStatusUpdated();
    }

    @EventListener
    public synchronized void onJobTerminal(
            TranslationJobTerminalEvent event
    ) {
        if (event.jobId().equals(activeJobId)) {
            terminalReceived = true;
        }

        coordinator.markTerminal(event.jobId());
    }

    @EventListener
    public synchronized void onSugoiSlotReleased(
            SugoiSlotReleasedEvent event
    ) {
        if (!event.jobId().equals(activeJobId)) {
            return;
        }

        log.info(
                "Sugoi slot released for single job {}",
                event.jobId()
        );

        activeJobId = null;
        terminalReceived = false;
        activeStartedAt = null;
        activeTimedOut = false;
        dispatching = false;

        tryDispatch();
    }

    private void checkActiveTimeout() {
        if (activeJobId == null || activeStartedAt == null) {
            return;
        }

        if (activeTimedOut || terminalReceived) {
            return;
        }

        Duration elapsed = Duration.between(
                activeStartedAt,
                Instant.now()
        );

        if (elapsed.compareTo(timeout) < 0) {
            return;
        }

        String jobId = activeJobId;

        activeTimedOut = true;

        log.error(
                "Translation job {} timed out after {}",
                jobId,
                elapsed
        );

        registry.fail(
                jobId,
                "Translation timed out after " + timeout
        );
        coordinator.markTimedOut(jobId);

    }


    private synchronized void tryDispatch() {

        if (dispatching || activeJobId != null || coordinator.isOccupied()) {
            return;
        }

        var entity = persistence.findNextQueuedJob().orElse(null);

        if (entity == null) {
            return;
        }

        SingleTranslationJob job = registry.getJob(entity.getJobId());

        if (job == null
                || job.getStatus() != SingleTranslationJobStatus.QUEUED
                || job.getInputPath() == null) {
            log.warn("Skipping invalid queued job {}", entity.getJobId());
            return;
        }

        Path inputPath = job.getInputPath()
                .toAbsolutePath()
                .normalize();

        String relativePath = inputPath.toString()
                .replace('\\', '/');

        int filesIndex = relativePath.lastIndexOf("/files/");

        // Storage root may already be Sugoi's files directory.
        String sugoiPath;
        if (filesIndex >= 0) {
            sugoiPath = relativePath.substring(filesIndex + 1);
        } else {
            // The configured storage root is /mnt/sugoi-files,
            // which maps to the Sugoi protocol's "files/" root.
            sugoiPath = "files/"
                    + persistenceRelativeInputPath(inputPath, job);
        }
        if (!coordinator.tryAcquire(job.getJobId(), "single")) {
            return;
        }

        if (!registry.markSubmitted(job.getJobId())) {
            coordinator.cancelAcquire(job.getJobId());
            return;
        }
        activeJobId = job.getJobId();
        terminalReceived = false;
        dispatching = true;

        activeStartedAt = Instant.now();
        activeTimedOut = false;

        log.info("Dispatching translation job {}", activeJobId);

        CompletableFuture<Void> sendFuture;

        try {
            sendFuture = commandService.translateMangaImage(
                    job.getJobId(),
                    sugoiPath,
                    job.getSourceLang(),
                    job.getTargetLang(),
                    job.getTranslator()
            );
        } catch (RuntimeException e) {
            dispatching = false;
            activeTimedOut = true;

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

        String dispatchedJobId = job.getJobId();

        sendFuture.whenComplete((ignored, error) -> {
            synchronized (SingleTranslationQueue.this) {
                dispatching = false;

                if (!dispatchedJobId.equals(activeJobId)) {
                    return;
                }

                if (error != null) {
                    coordinator.markTimedOut(dispatchedJobId);
                    log.error(
                            "Failed to send command for job {}: {}",
                            dispatchedJobId,
                            rootMessage(error)
                    );

                    registry.fail(
                            dispatchedJobId,
                            "Failed to submit translation command: "
                                    + rootMessage(error)
                    );

                    activeTimedOut = true;
                    return;
                }

                log.info(
                        "Command sent to Sugoi for job {}",
                        dispatchedJobId
                );
            }
        });
    }

    private String persistenceRelativeInputPath(
            Path inputPath,
            SingleTranslationJob job
    ) {
        // The expected layout is uploads/<jobId>/input.<ext>.
        return "uploads/" + job.getJobId() + "/"
                + inputPath.getFileName();
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
