package com.shemhazai.translationservice.translation.single;

import lombok.Getter;
import lombok.Setter;

import java.nio.file.Path;
import java.time.Instant;

@Getter
@Setter
public class SingleTranslationJob {

    private final String jobId;
    private final String originalFilename;
    private final String sourceLang;
    private final String targetLang;
    private final String translator;
    private final Instant createdAt;

    private volatile Instant updatedAt;
    private volatile SingleTranslationJobStatus status;
    private volatile Path inputPath;

    private volatile String stage;
    private volatile int currentStep;
    private volatile int totalSteps;
    private volatile String progressDetail;

    private volatile SingleTranslationResult result;
    private volatile String errorMessage;


    public SingleTranslationJob(
            String jobId,
            String originalFilename,
            String sourceLang,
            String targetLang,
            String translator
    ) {
        this.jobId = jobId;
        this.originalFilename = originalFilename;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.translator = translator;

        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.status = SingleTranslationJobStatus.QUEUED;
    }

    public synchronized void updateStatus(SingleTranslationJobStatus status) {
        this.status = status;
        touch();
    }

    public synchronized void updateProgress(
            String stage,
            int currentStep,
            int totalSteps,
            String detail
    ) {
        this.stage = stage;
        this.currentStep = currentStep;
        this.totalSteps = totalSteps;
        this.progressDetail = detail;
        this.status = SingleTranslationJobStatus.TRANSLATING;
        touch();
    }

    public synchronized void setInputPath(Path inputPath) {
        this.inputPath = inputPath;
        touch();
    }

    public synchronized void complete(SingleTranslationResult result) {
        this.result = result;
        this.status = SingleTranslationJobStatus.COMPLETED;
        touch();
    }

    public synchronized void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = SingleTranslationJobStatus.FAILED;
        touch();
    }

    public synchronized void restoreMetadata(
            SingleTranslationJobStatus status,
            Instant updatedAt,
            String stage,
            int currentStep,
            int totalSteps,
            String progressDetail,
            String errorMessage
    ) {
        this.status = status;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        this.stage = stage;
        this.currentStep = currentStep;
        this.totalSteps = totalSteps;
        this.progressDetail = progressDetail;
        this.errorMessage = errorMessage;
    }


    private void touch() {
        this.updatedAt = Instant.now();
    }

}
