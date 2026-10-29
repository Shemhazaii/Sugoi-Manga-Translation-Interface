package com.shemhazai.translationservice.sugoi;

import com.shemhazai.translationservice.config.SugoiInitializationProperties;
import com.shemhazai.translationservice.config.SugoiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SugoiInitializationService {

    private final SugoiWebsocketClient client;
    private final SugoiCommandService commands;
    private final SugoiStateService state;
    private final SugoiProperties connectionProperties;
    private final SugoiInitializationProperties properties;

    private final AtomicBoolean initializationInProgress =
            new AtomicBoolean(false);

    private final AtomicBoolean recoveryRequested =
            new AtomicBoolean(false);

    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile boolean initializationComplete = false;
    private volatile String failureReason;

    public SugoiInitializationService(
            SugoiWebsocketClient client, SugoiCommandService commands, SugoiStateService state, SugoiProperties connectionProperties, SugoiInitializationProperties properties
    ) {
        this.client = client;
        this.commands = commands;
        this.state = state;
        this.connectionProperties = connectionProperties;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        // Jalankan di background agar aplikasi tetap bisa melayani
        // endpoint readiness ketika Sugoi sedang loading atau gagal.
        CompletableFuture.runAsync(this::initializeSafely);
    }

    @EventListener
    public void onWebSocketReconnected(
            SugoiWebSocketReconnectedEvent event
    ) {
        if (!started.get()) {
            log.debug(
                    "Ignoring reconnect event before application startup initialization"
            );
            return;
        }

        log.info(
                "Sugoi WebSocket reconnected; re-initializing translator "
                        + "and manga module"
        );

        CompletableFuture.runAsync(this::initializeSafely);
    }

    private final AtomicBoolean recoveryInProgress =
            new AtomicBoolean(false);

    public void recoverAfterReconnect() {
        if (!recoveryInProgress.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                initializationComplete = false;
                failureReason = null;

                log.info("Reinitializing Sugoi after WebSocket reconnect");

                initialize();

                initializationComplete = true;

                log.info("Sugoi recovery initialization completed");

            } catch (Exception exception) {
                failureReason = rootMessage(exception);

                log.error(
                        "Sugoi recovery initialization failed: {}",
                        failureReason,
                        exception
                );

            } finally {
                recoveryInProgress.set(false);
            }
        });
    }

    private void initializeSafely() {
        if (!initializationInProgress.compareAndSet(false, true)) {
            log.info(
                    "Sugoi initialization is already running; "
                            + "queuing another initialization"
            );
            recoveryRequested.set(true);
            return;
        }

        try {
            doInitializeSafely();
        } finally {
            initializationInProgress.set(false);

            if (recoveryRequested.getAndSet(false)) {
                log.info(
                        "Running queued Sugoi initialization after previous run"
                );
                CompletableFuture.runAsync(this::initializeSafely);
            }
        }
    }

    private void doInitializeSafely() {
        initializationComplete = false;
        failureReason = null;

        try {
            initialize();
            initializationComplete = true;

            log.info(
                    "Sugoi initialization completed: backend={}, language={}->{}",
                    properties.getBackend(),
                    properties.getSourceLang(),
                    properties.getTargetLang()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failureReason = "Sugoi initialization was interrupted";
            log.error(failureReason, exception);
        } catch (Exception exception) {
            failureReason = rootMessage(exception);
            log.error(
                    "Sugoi initialization failed: {}",
                    failureReason,
                    exception
            );
        }

        if (!isReady()) {
            log.warn(
                    "Sugoi initialization finished, but readiness checks failed: {}",
                    failureReason
            );
        }
    }

    private void initialize() throws Exception {
        connectIfNecessary();
        ensureTranslatorReady();
        ensureMangaModuleReady();



    }

    private void connectIfNecessary() throws Exception {
        if (client.isConnected()) {
            log.info("Sugoi WebSocket is already connected");
            return;
        }

        log.info("Connecting to Sugoi WebSocket");

        client.connect().get(
                connectionProperties.connectTimeout().toMillis(),
                TimeUnit.MILLISECONDS
        );

        if (!client.isConnected()) {
            throw new IllegalStateException(
                    "WebSocket connect completed but client is not connected"
            );
        }

        log.info("Sugoi WebSocket connected");
    }

    private void ensureTranslatorReady() throws Exception {
        String backend = properties.getBackend();

        JsonNode status = requestTranslatorStatus();

        if (!status.path("success").asBoolean(false)) {
            throw new IllegalStateException(
                    "Sugoi translator status request was unsuccessful: "
                            + status
            );
        }

        if (!containsBackend(status, backend)) {
            log.info("Loading Sugoi translator backend: {}", backend);

            long version = state.getTranslatorVersion();

            sendAndWait(
                    commands.loadTranslator(backend),
                    properties.getTranslatorTimeout()
            );

            status = state.awaitTranslatorStatusAfter(
                    version,
                    event -> isTranslatorEvent(event)
                            && (!event.path("success").asBoolean(false)
                            || backend.equals(
                            event.path("backend").asString()
                    )),
                    properties.getTranslatorTimeout()
            );

            requireSuccess(status, "loading translator " + backend);

            if (!containsBackend(status, backend)) {
                throw new IllegalStateException(
                        "Translator backend was not loaded: " + backend
                );
            }
        }

        if (!backend.equals(status.path("active_backend").asString())) {
            log.info("Activating Sugoi translator backend: {}", backend);

            long version = state.getTranslatorVersion();

            sendAndWait(
                    commands.setTranslator(backend),
                    properties.getTranslatorTimeout()
            );

            status = state.awaitTranslatorStatusAfter(
                    version,
                    event -> isTranslatorEvent(event)
                            && (!event.path("success").asBoolean(false)
                            || backend.equals(
                            event.path("active_backend").asString()
                    )),
                    properties.getTranslatorTimeout()
            );

            requireSuccess(status, "activating translator " + backend);
        }

        if (!backend.equals(status.path("active_backend").asString())) {
            throw new IllegalStateException(
                    "Translator backend is not active: expected "
                            + backend + ", got "
                            + status.path("active_backend").asString()
            );
        }
    }

    private JsonNode requestTranslatorStatus() throws Exception {
        long version = state.getTranslatorVersion();

        sendAndWait(
                commands.getTranslatorStatus(),
                properties.getTranslatorTimeout()
        );

        return state.awaitTranslatorStatusAfter(
                version,
                this::isTranslatorEvent,
                properties.getTranslatorTimeout()
        );
    }

    private void ensureMangaModuleReady() throws Exception {
        JsonNode status = requestMangaStatus();
        String backend = properties.getBackend();


        boolean alreadyReady =
                "ready".equals(status.path("state").asString())
                        && backend.equals(
                        status.path("translator_backend").asString()
                )
                        && status.path("translator_ready").asBoolean(false)
                        && status.path("error").isNull();

        if (alreadyReady) {
            log.info("Sugoi manga module is already ready");
            return;
        }

        String currentState = status.path("state").asString();

        if (!"loading".equals(currentState)) {
            log.info(
                    "Loading Sugoi manga module: {} -> {}",
                    properties.getSourceLang(),
                    properties.getTargetLang()
            );

            long version = state.getMangaStatusVersion();

            sendAndWait(
                    commands.loadMangaModule(
                            properties.getSourceLang(),
                            properties.getTargetLang()
                    ),
                    properties.getMangaTimeout()
            );

            status = state.awaitMangaStatusAfter(
                    version,
                    this::isTerminalMangaStatus,
                    properties.getMangaTimeout()
            );
        } else {
            log.info(
                    "Sugoi manga module is already loading; waiting for completion"
            );

            status = state.awaitMangaStatus(
                    this::isTerminalMangaStatus,
                    properties.getMangaTimeout()
            );
        }

        requireMangaReady(status);
    }

    private JsonNode requestMangaStatus() throws Exception {
        long version = state.getMangaStatusVersion();

        sendAndWait(
                commands.getMangaModuleStatus(),
                properties.getMangaTimeout()
        );

        return state.awaitMangaStatusAfter(
                version,
                event -> "manga_module_status".equals(
                        event.path("type").asString()
                ),
                properties.getMangaTimeout()
        );
    }

    private boolean isTranslatorEvent(JsonNode event) {
        return "translator_status".equals(
                event.path("type").asString()
        );
    }

    private boolean containsBackend(JsonNode status, String backend) {
        JsonNode loaded = status.path("loaded_backends");

        if (!loaded.isArray()) {
            return false;
        }

        for (JsonNode item : loaded) {
            if (backend.equals(item.asString())) {
                return true;
            }
        }

        return false;
    }

    private boolean isTerminalMangaStatus(JsonNode status) {
        String current = status.path("state").asString();

        return "ready".equals(current)
                || "error".equals(current)
                || "failed".equals(current);
    }

    private void requireSuccess(JsonNode status, String operation) {
        if (!status.path("success").asBoolean(false)) {
            throw new IllegalStateException(
                    "Sugoi failed while " + operation + ": " + status
            );
        }
    }

    private void requireMangaReady(JsonNode status) {
        if (!"ready".equals(status.path("state").asString())) {
            throw new IllegalStateException(
                    "Sugoi manga module failed to load: " + status
            );
        }

        if (!status.path("error").isNull()) {
            throw new IllegalStateException(
                    "Sugoi manga module reported an error: "
                            + status.path("error")
            );
        }

        if (!status.path("translator_ready").asBoolean(false)) {
            throw new IllegalStateException(
                    "Sugoi manga module reports translator_ready=false"
            );
        }

        if (!properties.getBackend().equals(
                status.path("translator_backend").asString()
        )) {
            throw new IllegalStateException(
                    "Manga module translator backend does not match "
                            + properties.getBackend()
            );
        }
    }

    private void sendAndWait(
            CompletableFuture<Void> operation,
            java.time.Duration timeout
    ) throws Exception {
        try {
            operation.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception causeException) {
                throw causeException;
            }
            throw new IllegalStateException(
                    "Sugoi WebSocket command failed",
                    cause
            );
        } catch (TimeoutException exception) {
            throw new TimeoutException(
                    "Timed out sending Sugoi WebSocket command after "
                            + timeout
            );
        }
    }

    public boolean isReady() {
        if (!initializationComplete || !client.isConnected()) {
            return false;
        }

        JsonNode translator = state.getTranslatorStatus();
        JsonNode manga = state.getMangaModuleStatus();

        return translator != null
                && translator.path("success").asBoolean(false)
                && properties.getBackend().equals(
                translator.path("active_backend").asString()
        )
                && manga != null
                && "ready".equals(manga.path("state").asString())
                && manga.path("error").isNull()
                && manga.path("translator_ready").asBoolean(false)
                && properties.getBackend().equals(
                manga.path("translator_backend").asString()
        );
    }

    public String getFailureReason() {
        return failureReason;
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;

        while (current.getCause() != null
                && current.getCause() != current) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

}
