
package com.shemhazai.translationservice.sugoi;

import com.shemhazai.translationservice.config.SugoiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SugoiWebsocketClient {

    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1;
    private static final long MAX_RECONNECT_DELAY_SECONDS = 30;

    private final SugoiProperties properties;
    private final HttpClient httpClient;
    private final SugoiMessageHandler messageHandler;
    private final ApplicationEventPublisher eventPublisher;

    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "sugoi-websocket-reconnect"
                );
                thread.setDaemon(true);
                return thread;
            });

    private final AtomicBoolean reconnectEnabled =
            new AtomicBoolean(true);

    private volatile WebSocket webSocket;
    private volatile CompletableFuture<Void> connectionFuture;
    private volatile ScheduledFuture<?> reconnectFuture;

    private int reconnectAttempts;

    public SugoiWebsocketClient(
            SugoiProperties properties,
            SugoiMessageHandler messageHandler, ApplicationEventPublisher eventPublisher
    ) {
        this.properties = properties;
        this.messageHandler = messageHandler;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        this.eventPublisher = eventPublisher;
    }

    public synchronized CompletableFuture<Void> connect() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        if (connectionFuture != null && !connectionFuture.isDone()) {
            return connectionFuture;
        }

        reconnectEnabled.set(true);
        cancelScheduledReconnect();

        URI uri = URI.create(properties.websocketUrl());

        log.info("Connecting to Sugoi WebSocket: {}", uri);

        CompletableFuture<Void> attempt = httpClient
                .newWebSocketBuilder()
                .connectTimeout(properties.connectTimeout())
                .buildAsync(uri, new SugoiWebSocketListener())
                .thenAccept(socket -> {
                    // Reconnect attempts are incremented by scheduleReconnect().
                    boolean reconnected = reconnectAttempts > 0;

                    webSocket = socket;
                    reconnectAttempts = 0;

                    log.info("Connected to Sugoi WebSocket");

                    if (reconnected) {
                        log.info(
                                "Sugoi WebSocket reconnected; requesting re-initialization"
                        );
                        eventPublisher.publishEvent(
                                new SugoiWebSocketReconnectedEvent()
                        );
                    }
                });

        connectionFuture = attempt;

        attempt.whenComplete((ignored, error) -> {
            synchronized (SugoiWebsocketClient.this) {
                if (connectionFuture == attempt) {
                    connectionFuture = null;
                }
            }

            if (error != null) {
                log.warn(
                        "Failed to connect to Sugoi WebSocket: {}",
                        error.getMessage()
                );

                scheduleReconnect();
            }
        });

        return attempt;
    }

    public boolean isConnected() {
        WebSocket socket = webSocket;

        return socket != null
                && !socket.isInputClosed()
                && !socket.isOutputClosed();
    }

    public CompletableFuture<WebSocket> getConnection() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(webSocket);
        }

        return connect().thenApply(ignored -> webSocket);
    }

    public CompletableFuture<WebSocket> sendText(String message) {
        return getConnection()
                .thenCompose(socket -> socket.sendText(message, true));
    }

    public CompletableFuture<Void> sendCommand(String json) {
        return getConnection()
                .thenCompose(socket -> socket.sendText(json, true))
                .thenApply(ignored -> null);
    }

    public CompletableFuture<Void> disconnect() {
        reconnectEnabled.set(false);

        synchronized (this) {
            cancelScheduledReconnect();
        }

        WebSocket socket = webSocket;
        webSocket = null;

        if (socket == null) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("Disconnecting from Sugoi WebSocket");

        return socket.sendClose(
                WebSocket.NORMAL_CLOSURE,
                "Translation service shutting down"
        ).thenApply(ignored -> null);
    }

    private synchronized void scheduleReconnect() {
        if (!reconnectEnabled.get()) {
            return;
        }

        if (isConnected()) {
            return;
        }

        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            return;
        }

        int exponent = Math.min(reconnectAttempts, 5);

        long delay = Math.min(
                INITIAL_RECONNECT_DELAY_SECONDS * (1L << exponent),
                MAX_RECONNECT_DELAY_SECONDS
        );

        reconnectAttempts++;

        log.info(
                "Scheduling Sugoi WebSocket reconnect in {} seconds (attempt {})",
                delay,
                reconnectAttempts
        );

        reconnectFuture = reconnectScheduler.schedule(() -> {
            synchronized (SugoiWebsocketClient.this) {
                reconnectFuture = null;
            }

            if (!reconnectEnabled.get() || isConnected()) {
                return;
            }

            connect().whenComplete((ignored, error) -> {
                if (error != null) {
                    log.debug(
                            "Sugoi WebSocket reconnect attempt failed: {}",
                            error.getMessage()
                    );
                }
            });
        }, delay, TimeUnit.SECONDS);
    }

    private synchronized void cancelScheduledReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private class SugoiWebSocketListener implements WebSocket.Listener {

        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            log.info("Sugoi WebSocket opened");
            socket.request(1);
        }

        @Override
        public synchronized CompletionStage<?> onText(
                WebSocket socket,
                CharSequence data,
                boolean last
        ) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);

                try {
                    messageHandler.handle(message);
                } catch (Exception exception) {
                    log.error(
                            "Failed to handle Sugoi WebSocket message",
                            exception
                    );
                }
            }

            socket.request(1);

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket socket,
                int statusCode,
                String reason
        ) {
            log.warn(
                    "Sugoi WebSocket closed: status={}, reason={}",
                    statusCode,
                    reason
            );

            if (webSocket == socket) {
                webSocket = null;
                scheduleReconnect();
            }

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            log.error("Sugoi WebSocket error", error);

            if (webSocket == socket) {
                webSocket = null;
                scheduleReconnect();
            }
        }
    }
}
