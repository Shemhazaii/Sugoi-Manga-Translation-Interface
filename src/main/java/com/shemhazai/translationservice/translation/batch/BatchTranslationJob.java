
package com.shemhazai.translationservice.translation.batch;

import lombok.Getter;

import java.nio.file.Path;
import java.time.Instant;

@Getter
public class BatchTranslationJob {

    private final String jobId;
    private final String originalFilename;
    private final String sourceLang;
    private final String targetLang;
    private final String translator;

    private final Path inputArchive;
    private final Path inputFolder;
    private final Path outputFolder;

    private Path resultArchive;

    private Instant createdAt;
    private Instant updatedAt;
    private BatchTranslationStatus status;

    private int totalImages;
    private int completedImages;
    private int failedImages;

    private int currentImageIndex;
    private String currentImagePath;

    private String currentStage;
    private int currentStep;
    private int currentTotalSteps;
    private String progressDetail;

    private String errorMessage;

    public BatchTranslationJob(
            String jobId,
            String originalFilename,
            String sourceLang,
            String targetLang,
            String translator,
            Path inputArchive,
            Path inputFolder,
            Path outputFolder
    ) {
        this.jobId = jobId;
        this.originalFilename = originalFilename;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.translator = translator;
        this.inputArchive = inputArchive;
        this.inputFolder = inputFolder;
        this.outputFolder = outputFolder;

        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = BatchTranslationStatus.QUEUED;
    }

    // Compatibility constructor for existing callers.
    public BatchTranslationJob(
            String jobId,
            String sourceLang,
            String targetLang,
            String translator,
            Path inputFolder,
            Path outputFolder
    ) {
        this(
                jobId,
                jobId + ".zip",
                sourceLang,
                targetLang,
                translator,
                inputFolder.resolveSibling(jobId + ".zip"),
                inputFolder,
                outputFolder
        );
    }

    public synchronized void updateStatus(
            BatchTranslationStatus status
    ) {
        this.status = status;
        touch();
    }

    public synchronized void imagesFound(int totalImages) {
        this.totalImages = totalImages;
        this.status = BatchTranslationStatus.TRANSLATING;
        touch();
    }

    public synchronized void imageStarted(
            String imagePath,
            int imageIndex,
            int totalImages
    ) {
        this.currentImagePath = imagePath;
        this.currentImageIndex = imageIndex;
        this.totalImages = totalImages;
        this.status = BatchTranslationStatus.TRANSLATING;
        touch();
    }

    public synchronized void imageProgress(
            String imagePath,
            int current,
            int total,
            String stage,
            String detail,
            int imageIndex,
            int totalImages
    ) {
        this.currentImagePath = imagePath;
        this.currentStep = current;
        this.currentTotalSteps = total;
        this.currentStage = stage;
        this.progressDetail = detail;
        this.currentImageIndex = imageIndex;
        this.totalImages = totalImages;
        this.status = BatchTranslationStatus.TRANSLATING;
        touch();
    }

    public synchronized void imageCompleted(
            String imagePath,
            int imageIndex,
            int totalImages
    ) {
        this.currentImagePath = imagePath;
        this.currentImageIndex = imageIndex;
        this.totalImages = totalImages;
        this.completedImages++;
        touch();
    }

    public synchronized void complete(
            int total,
            int succeeded,
            int failed
    ) {
        this.totalImages = total;
        this.completedImages = succeeded;
        this.failedImages = failed;
        this.status = BatchTranslationStatus.PACKAGING;
        touch();
    }

    public synchronized void setResultArchive(Path resultArchive) {
        this.resultArchive = resultArchive;
        touch();
    }

    public synchronized void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = BatchTranslationStatus.FAILED;
        touch();
    }

    /**
     * Restores mutable metadata from PostgreSQL.
     * Used when rebuilding a runtime job after application startup.
     */
    public synchronized void restoreMetadata(
            BatchTranslationStatus status,
            Instant createdAt,
            Instant updatedAt,
            int totalImages,
            int completedImages,
            int failedImages,
            Integer currentImageIndex,
            String currentImagePath,
            String currentStage,
            int currentStep,
            int currentTotalSteps,
            String progressDetail,
            String errorMessage,
            Path resultArchive
    ) {
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.totalImages = totalImages;
        this.completedImages = completedImages;
        this.failedImages = failedImages;
        this.currentImageIndex =
                currentImageIndex == null ? 0 : currentImageIndex;
        this.currentImagePath = currentImagePath;
        this.currentStage = currentStage;
        this.currentStep = currentStep;
        this.currentTotalSteps = currentTotalSteps;
        this.progressDetail = progressDetail;
        this.errorMessage = errorMessage;
        this.resultArchive = resultArchive;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
