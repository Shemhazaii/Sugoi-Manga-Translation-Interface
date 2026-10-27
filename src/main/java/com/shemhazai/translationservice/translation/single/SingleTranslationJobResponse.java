
package com.shemhazai.translationservice.translation.single;

import java.time.Instant;

public record SingleTranslationJobResponse(
        String jobId,
        SingleTranslationJobStatus status,
        String sourceLang,
        String targetLang,
        String translator,
        Instant createdAt,
        Instant updatedAt,
        String stage,
        int currentStep,
        int totalSteps,
        String progressDetail,
        String errorMessage,
        String statusUrl,
        String resultUrl
) {
    public static SingleTranslationJobResponse from(
            SingleTranslationJob job
    ) {
        String baseUrl = "/api/v1/translations/" + job.getJobId();

        return new SingleTranslationJobResponse(
                job.getJobId(),
                job.getStatus(),
                job.getSourceLang(),
                job.getTargetLang(),
                job.getTranslator(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getStage(),
                job.getCurrentStep(),
                job.getTotalSteps(),
                job.getProgressDetail(),
                job.getErrorMessage(),
                baseUrl,
                baseUrl + "/result"
        );
    }
}
