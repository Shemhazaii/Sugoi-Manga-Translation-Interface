package com.shemhazai.translationservice.sugoi;

import com.shemhazai.translationservice.config.SugoiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
@Component
public class SugoiWebsocketClient {



    private final SugoiProperties properties;
    private final HttpClient httpClient;
    private final SugoiMessageHandler messageHandler;

    private volatile WebSocket webSocket;

    public SugoiWebsocketClient(SugoiProperties properties, SugoiMessageHandler messageHandler) {
        this.properties = properties;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();

        this.messageHandler = messageHandler;
    }

    public CompletableFuture<Void> connect() {
        if (isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        URI uri = URI.create(properties.websocketUrl());

        log.info("Connecting to Sugoi WebSocket: {}", uri);

        return httpClient.newWebSocketBuilder()
                .connectTimeout(properties.connectTimeout())
                .buildAsync(uri, new SugoiWebSocketListener())
                .thenAccept(socket -> {
                    webSocket = socket;
                    log.info("Connected to Sugoi WebSocket");
                });
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
                .thenCompose(socket ->
                        socket.sendText(message, true)
                );
    }

    public CompletableFuture<Void> disconnect() {
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

    public CompletableFuture<Void> sendCommand(String json) {
        return getConnection()
                .thenCompose(socket ->
                        socket.sendText(json, true)
                )
                .thenApply(ignored -> null);
    }

    private class SugoiWebSocketListener implements WebSocket.Listener {

        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("Sugoi WebSocket opened");

            webSocket.request(1);
        }

        @Override
        public synchronized CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);

                messageHandler.handle(message);
            }

            webSocket.request(1);

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket,
                int statusCode,
                String reason
        ) {
            log.info(
                    "Sugoi WebSocket closed: status={}, reason={}",
                    statusCode,
                    reason
            );

            if (SugoiWebsocketClient.this.webSocket == webSocket) {
                SugoiWebsocketClient.this.webSocket = null;
            }

            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(
                WebSocket webSocket,
                Throwable error
        ) {
            log.error(
                    "Sugoi WebSocket error",
                    error
            );

            if (SugoiWebsocketClient.this.webSocket == webSocket) {
                SugoiWebsocketClient.this.webSocket = null;
            }
        }

    }

}
