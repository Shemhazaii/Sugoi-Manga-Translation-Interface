
package com.shemhazai.translationservice.translation.batch;

import java.time.Instant;

public record BatchTranslationJobResponse(
        String jobId,
        String originalFilename,
        String sourceLang,
        String targetLang,
        String translator,
        String status,
        Instant createdAt,
        Instant updatedAt,
        int totalImages,
        int completedImages,
        int failedImages,
        int currentImageIndex,
        String currentImagePath,
        String currentStage,
        int currentStep,
        int currentTotalSteps,
        String progressDetail,
        String errorMessage,
        String statusUrl,
        String resultUrl
) {
    public static BatchTranslationJobResponse from(BatchTranslationJob job) {
        String base = "/api/v1/translations/batch/" + job.getJobId();

        return new BatchTranslationJobResponse(
                job.getJobId(),
                job.getOriginalFilename(),
                job.getSourceLang(),
                job.getTargetLang(),
                job.getTranslator(),
                job.getStatus().name(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getTotalImages(),
                job.getCompletedImages(),
                job.getFailedImages(),
                job.getCurrentImageIndex(),
                job.getCurrentImagePath(),
                job.getCurrentStage(),
                job.getCurrentStep(),
                job.getCurrentTotalSteps(),
                job.getProgressDetail(),
                job.getErrorMessage(),
                base,
                base + "/result"
        );
    }
}
