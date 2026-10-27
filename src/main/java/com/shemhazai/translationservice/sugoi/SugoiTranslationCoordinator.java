
package com.shemhazai.translationservice.sugoi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

@Slf4j
@Component
public class SugoiTranslationCoordinator {

    private final SugoiStateService stateService;
    private final ApplicationEventPublisher eventPublisher;

    private String activeJobId;
    private String activeJobType;

    private long statusVersionAtDispatch;
    private long statusVersionAtBusy;
    private long statusVersionAtTerminal;

    private boolean busySeen;
    private boolean terminalReceived;
    private boolean timedOut;

    private Instant startedAt;

    public SugoiTranslationCoordinator(
            SugoiStateService stateService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.stateService = stateService;
        this.eventPublisher = eventPublisher;
    }

    public synchronized boolean tryAcquire(
            String jobId,
            String jobType
    ) {
        if (activeJobId != null || !isSugoiIdle()) {
            return false;
        }

        activeJobId = jobId;
        activeJobType = jobType;
        statusVersionAtDispatch =
                stateService.getMangaStatusVersion();

        statusVersionAtBusy = 0;
        statusVersionAtTerminal = 0;
        busySeen = false;
        terminalReceived = false;
        timedOut = false;
        startedAt = Instant.now();

        log.info(
                "Acquired Sugoi slot: type={}, jobId={}",
                jobType,
                jobId
        );

        return true;
    }

    public synchronized void cancelAcquire(String jobId) {
        if (!jobId.equals(activeJobId)) {
            return;
        }

        if (busySeen || terminalReceived) {
            log.warn(
                    "Cannot cancel Sugoi slot for job {} because Sugoi activity was observed",
                    jobId
            );
            return;
        }

        releaseSlot("dispatch cancelled before command submission");
    }

    public synchronized void onModuleStatusUpdated() {
        if (activeJobId == null) {
            return;
        }

        long version = stateService.getMangaStatusVersion();

        if (version <= statusVersionAtDispatch) {
            return;
        }

        if (!isSugoiIdle()) {
            busySeen = true;
            statusVersionAtBusy = version;
            return;
        }

        // Terminal must be followed by a NEW idle status update.
        if (terminalReceived && version > statusVersionAtTerminal) {
            releaseSlot("terminal event followed by fresh idle status");
            return;
        }

        // A timed-out job must have been observed busy, then idle.
        if (timedOut
                && busySeen
                && version > statusVersionAtBusy) {
            releaseSlot("timed-out job returned to idle");
        }
    }

    public synchronized void markTerminal(String jobId) {
        if (!jobId.equals(activeJobId)) {
            return;
        }

        terminalReceived = true;
        statusVersionAtTerminal =
                stateService.getMangaStatusVersion();

        onModuleStatusUpdated();
    }

    public synchronized void markTimedOut(String jobId) {
        if (!jobId.equals(activeJobId)) {
            return;
        }

        timedOut = true;
        onModuleStatusUpdated();
    }

    public synchronized boolean isActive(String jobId) {
        return jobId != null && jobId.equals(activeJobId);
    }

    public synchronized boolean isOccupied() {
        return activeJobId != null;
    }

    public synchronized String getActiveJobId() {
        return activeJobId;
    }

    public synchronized String getActiveJobType() {
        return activeJobType;
    }

    public synchronized Instant getStartedAt() {
        return startedAt;
    }

    private boolean isSugoiIdle() {
        JsonNode status = stateService.getMangaModuleStatus();

        return status != null
                && "ready".equals(status.path("state").asString())
                && status.path("active_jobs").isArray()
                && status.path("active_jobs").isEmpty();
    }

    private void releaseSlot(String reason) {
        String releasedJobId = activeJobId;
        String releasedJobType = activeJobType;

        activeJobId = null;
        activeJobType = null;
        statusVersionAtDispatch = 0;
        statusVersionAtBusy = 0;
        statusVersionAtTerminal = 0;
        busySeen = false;
        terminalReceived = false;
        timedOut = false;
        startedAt = null;

        log.info(
                "Released Sugoi slot: type={}, jobId={}, reason={}",
                releasedJobType,
                releasedJobId,
                reason
        );

        eventPublisher.publishEvent(
                new SugoiSlotReleasedEvent(
                        releasedJobId,
                        releasedJobType
                )
        );
    }
}
