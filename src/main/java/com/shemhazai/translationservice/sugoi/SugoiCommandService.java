package com.shemhazai.translationservice.sugoi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParseException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class SugoiCommandService {

    private final ObjectMapper objectMapper;
    private final SugoiWebsocketClient client;

    public SugoiCommandService(ObjectMapper objectMapper, SugoiWebsocketClient client) {
        this.objectMapper = objectMapper;
        this.client = client;
    }

    public CompletableFuture<Void> getTranslatorStatus() {
        return send(Map.of("type", "get_translator_status"));
    }

    public CompletableFuture<Void> getMangaModuleStatus() {
        return send(Map.of("type", "get_manga_module_status"));
    }

    public CompletableFuture<Void> loadTranslator(String backend) {
        return send(Map.of(
                "type", "load_translator",
                "backend", backend
        ));
    }

    public CompletableFuture<Void> setTranslator(String backend) {
        return send(Map.of(
                "type", "set_translator",
                "backend", backend
        ));
    }

    public CompletableFuture<Void> loadMangaModule(String sourceLang, String targetLang) {
        return send(Map.of(
                "type", "load_manga_module",
                "source_lang", sourceLang,
                "target_lang", targetLang
        ));
    }

    public CompletableFuture<Void> translateMangaImage(String jobId, String filePath, String sourceLang, String targetLang, String translator) {
        return send(Map.of(
                "type", "translate_manga_image",
                "job_id", jobId,
                "file_path", filePath,
                "source_lang", sourceLang,
                "target_lang", targetLang,
                "translator", translator,
                "save_step_outputs", false,
                "persist_result", false
        ));
    }

    public CompletableFuture<Void> translateMangaFolder(
            String jobId,
            String folderPath,
            String sourceLang,
            String targetLang,
            String translator,
            String outputFolder
    ) {
        return send(Map.of(
                "type", "translate_manga_folder",
                "job_id", jobId,
                "folder_path", folderPath,
                "source_lang", sourceLang,
                "target_lang", targetLang,
                "translator", translator,
                "output_folder", outputFolder
        ));
    }

    public void sendAndWait(
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


    private CompletableFuture<Void> send(Map<String, Object> command) {
        try {
            String json = objectMapper.writeValueAsString(command);
            return client.sendCommand(json);
        } catch (JsonParseException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
