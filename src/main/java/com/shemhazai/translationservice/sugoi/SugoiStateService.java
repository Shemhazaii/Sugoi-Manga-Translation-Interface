package com.shemhazai.translationservice.sugoi;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Service
public class SugoiStateService {

    private final AtomicReference<JsonNode> translatorStatus =
            new AtomicReference<>();
    private final AtomicReference<JsonNode> mangaModuleStatus =
            new AtomicReference<>();
    private final AtomicReference<JsonNode> mangaModuleProgress =
            new AtomicReference<>();

    private final AtomicLong translatorVersion = new AtomicLong();
    private final AtomicLong mangaStatusVersion = new AtomicLong();

    private final Object monitor = new Object();

    public void updateTranslatorStatus(JsonNode status) {
        translatorStatus.set(status.deepCopy());
        translatorVersion.incrementAndGet();
        notifyWaiters();
    }

    public void updateMangaModuleStatus(JsonNode status) {
        mangaModuleStatus.set(status.deepCopy());
        mangaStatusVersion.incrementAndGet();
        notifyWaiters();
    }

    public void updateMangaModuleProgress(JsonNode progress) {
        mangaModuleProgress.set(progress.deepCopy());
        notifyWaiters();
    }

    public JsonNode getTranslatorStatus() {
        return copyOf(translatorStatus.get());
    }

    public JsonNode getMangaModuleStatus() {
        return copyOf(mangaModuleStatus.get());
    }

    public JsonNode getMangaModuleProgress() {
        return copyOf(mangaModuleProgress.get());
    }

    public long getTranslatorVersion() {
        return translatorVersion.get();
    }

    public long getMangaStatusVersion() {
        return mangaStatusVersion.get();
    }

    public JsonNode awaitTranslatorStatusAfter(
            long previousVersion,
            Predicate<JsonNode> condition,
            Duration timeout
    ) throws InterruptedException, TimeoutException {
        return await(
                translatorStatus,
                translatorVersion,
                previousVersion,
                condition,
                timeout
        );
    }

    public JsonNode awaitMangaStatus(
            Predicate<JsonNode> condition,
            Duration timeout
    ) throws InterruptedException, TimeoutException {
        return await(
                mangaModuleStatus,
                mangaStatusVersion,
                -1,
                condition,
                timeout
        );
    }

    public JsonNode awaitMangaStatusAfter(
            long previousVersion,
            Predicate<JsonNode> condition,
            Duration timeout
    ) throws InterruptedException, TimeoutException {
        return await(
                mangaModuleStatus,
                mangaStatusVersion,
                previousVersion,
                condition,
                timeout
        );
    }

    public boolean isMangaModuleReady() {
        JsonNode status = mangaModuleStatus.get();

        return status != null
                && "ready".equals(status.path("state").asString());
    }

    private JsonNode await(
            AtomicReference<JsonNode> state,
            AtomicLong version,
            long previousVersion,
            Predicate<JsonNode> condition,
            Duration timeout
    ) throws InterruptedException, TimeoutException {

        long timeoutNanos = timeout.toNanos();
        long deadline = System.nanoTime() + timeoutNanos;

        synchronized (monitor) {
            while (true) {
                JsonNode current = state.get();

                if (version.get() > previousVersion
                        && current != null
                        && condition.test(current.deepCopy())) {
                    return current.deepCopy();
                }

                long remaining = deadline - System.nanoTime();

                if (remaining <= 0) {
                    throw new TimeoutException(
                            "Timed out waiting for Sugoi status after "
                                    + timeout
                    );
                }

                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
        }
    }

    private void notifyWaiters() {
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    private JsonNode copyOf(JsonNode value) {
        return value == null ? null : value.deepCopy();
    }

}
