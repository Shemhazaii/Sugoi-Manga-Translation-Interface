package com.shemhazai.translationservice.sugoi;

import com.shemhazai.translationservice.translation.batch.BatchTranslationJobRegistry;
import com.shemhazai.translationservice.translation.batch.BatchTranslationTerminalEvent;
import com.shemhazai.translationservice.translation.single.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class SugoiMessageHandler {

    private final ObjectMapper objectMapper;
    private final SugoiStateService stateService;
    private final SingleTranslationJobRegistry jobRegistry;
    private final BatchTranslationJobRegistry batchRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public SugoiMessageHandler(ObjectMapper objectMapper, SugoiStateService stateService, SingleTranslationJobRegistry jobRegistry, BatchTranslationJobRegistry batchRegistry, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.stateService = stateService;
        this.jobRegistry = jobRegistry;
        this.batchRegistry = batchRegistry;
        this.eventPublisher = eventPublisher;
    }

    public void handle(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.path("type").asString(null);

            if (type == null) {
                log.warn("Received Sugoi message without type: {}", message);
                return;
            }

            log.info("Received Sugoi event: {}", type);

            switch (type) {
                case "translator_status" ->
                        handleTranslatorStatus(root);

                case "manga_module_status" ->
                        handleMangaModuleStatus(root);

                case "manga_module_progress" ->
                        handleMangaModuleProgress(root);

                case "manga_translation_progress" ->
                        handleTranslationProgress(root);

                case "manga_translation_result" ->
                        handleTranslationResult(root);
                case "manga_folder_images_found" ->
                        handleFolderImagesFound(root);

                case "manga_folder_image_started" ->
                        handleFolderImageStarted(root);

                case "manga_folder_image_progress" ->
                        handleFolderImageProgress(root);

                case "manga_folder_image_complete" ->
                        handleFolderImageComplete(root);

                case "manga_folder_complete" ->
                        handleFolderComplete(root);


                default ->
                        log.debug("Unhandled Sugoi event type: {}", type);
            }

        } catch (Exception e) {
            log.error("Failed to process Sugoi WebSocket message: {}", message, e);
        }
    }

    private void handleTranslatorStatus(JsonNode message) {
        stateService.updateTranslatorStatus(message);
        log.info("Translator status: {}", message);
    }

    private void handleMangaModuleStatus(JsonNode message) {
        stateService.updateMangaModuleStatus(message);
        eventPublisher.publishEvent(
                new SugoiModuleStatusEvent(message)
        );
        log.info("Manga module status: {}", message);
    }

    private void handleMangaModuleProgress(JsonNode message) {
        stateService.updateMangaModuleProgress(message);
        log.info("Manga module progress: {}", message);
    }

    private void handleTranslationProgress(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn("Translation progress event has no job_id: {}", message);
            return;
        }

        SingleTranslationJob job = jobRegistry.getJob(jobId);

        if (job == null) {
            log.debug("Progress event received for unknown job: {}", jobId);
            return;
        }

        String stage = message.path("stage").asString("unknown");
        int current = message.path("current").asInt(0);
        int total = message.path("total").asInt(0);
        String detail = message.path("detail").asString(null);

        jobRegistry.updateProgress(
                jobId,
                stage,
                current,
                total,
                detail
        );

        log.info(
                "Translation progress: jobId={}, stage={}, step={}/{}, detail={}",
                jobId, stage, current, total, detail
        );
    }

    private void handleTranslationResult(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn("Translation result event has no job_id: {}", message);
            return;
        }

        String imageBase64 =
                message.path("translated_image_base64").asString(null);

        if (imageBase64 == null || imageBase64.isBlank()) {
            jobRegistry.fail(
                    jobId,
                    "Sugoi returned a translation result without image data"
            );
            log.warn("Translation result has no image data: jobId={}", jobId);
            return;
        }

        SingleTranslationResult result = new SingleTranslationResult(
                jobId,
                message.path("file_path").asString(null),
                imageBase64,
                message.path("output_path").asString(null),
                message.path("width").asInt(0),
                message.path("height").asInt(0)
        );

        boolean completed = jobRegistry.complete(jobId, result);

        if (completed) {
            eventPublisher.publishEvent(
                    new TranslationJobTerminalEvent(jobId)
            );
            log.info(
                    "Translation completed: jobId={}, width={}, height={}",
                    jobId, result.width(), result.height()
            );
        } else {
            log.debug(
                    "Translation result ignored for unknown or completed job: {}",
                    jobId
            );
        }
    }

    private void handleFolderImagesFound(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn(
                    "Folder images found event has no job_id: {}",
                    message
            );
            return;
        }

        int total = message.path("total").asInt(0);

        batchRegistry.imagesFound(
                jobId,
                total
        );

        log.info(
                "Batch images found: jobId={}, total={}",
                jobId,
                total
        );
    }

    private void handleFolderImageStarted(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn(
                    "Folder image started event has no job_id: {}",
                    message
            );
            return;
        }

        String imagePath =
                message.path("image_path").asString(null);

        int imageIndex =
                message.path("image_index").asInt(0);

        int totalImages =
                message.path("total_images").asInt(0);

        batchRegistry.imageStarted(
                jobId,
                imagePath,
                imageIndex,
                totalImages
        );

        log.info(
                "Batch image started: jobId={}, image={}/{}, path={}",
                jobId,
                imageIndex + 1,
                totalImages,
                imagePath
        );
    }

    private void handleFolderImageProgress(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn(
                    "Folder image progress event has no job_id: {}",
                    message
            );
            return;
        }

        String imagePath =
                message.path("image_path")
                        .asString(
                                message.path("file_path").asString(null)
                        );

        String stage =
                message.path("stage").asString("unknown");

        int current =
                message.path("current").asInt(0);

        int total =
                message.path("total").asInt(0);

        String detail =
                message.path("detail").asString(null);

        int imageIndex =
                message.path("image_index").asInt(0);

        int totalImages =
                message.path("total_images").asInt(0);

        batchRegistry.imageProgress(
                jobId,
                imagePath,
                current,
                total,
                stage,
                detail,
                imageIndex,
                totalImages
        );

        log.info(
                "Batch progress: jobId={}, image={}/{}, stage={}, step={}/{}, detail={}",
                jobId,
                imageIndex + 1,
                totalImages,
                stage,
                current,
                total,
                detail
        );
    }

    private void handleFolderImageComplete(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn(
                    "Folder image complete event has no job_id: {}",
                    message
            );
            return;
        }

        String imagePath =
                message.path("image_path").asString(null);

        int imageIndex =
                message.path("image_index").asInt(0);

        int totalImages =
                message.path("total_images").asInt(0);

        batchRegistry.imageCompleted(
                jobId,
                imagePath,
                imageIndex,
                totalImages
        );

        log.info(
                "Batch image completed: jobId={}, image={}/{}, output={}",
                jobId,
                imageIndex + 1,
                totalImages,
                message.path("output_path").asString(null)
        );
    }

    private void handleFolderComplete(JsonNode message) {
        String jobId = message.path("job_id").asString(null);

        if (jobId == null || jobId.isBlank()) {
            log.warn(
                    "Folder complete event has no job_id: {}",
                    message
            );
            return;
        }

        int total =
                message.path("total").asInt(0);

        int succeeded =
                message.path("succeeded").asInt(0);

        int failed =
                message.path("failed").asInt(0);

        boolean completed = batchRegistry.complete(
                jobId,
                total,
                succeeded,
                failed
        );

        if (!completed) {
            log.debug(
                    "Batch completion ignored for unknown or terminal job: {}",
                    jobId
            );
            return;
        }

        eventPublisher.publishEvent(
                new BatchTranslationTerminalEvent(jobId)
        );

        log.info(
                "Batch translation completed by Sugoi: jobId={}, total={}, succeeded={}, failed={}",
                jobId,
                total,
                succeeded,
                failed
        );
    }

}
