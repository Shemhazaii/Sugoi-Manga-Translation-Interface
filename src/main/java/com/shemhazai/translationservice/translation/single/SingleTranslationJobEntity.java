
package com.shemhazai.translationservice.translation.single;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "single_translation_jobs",
        indexes = {
                @Index(
                        name = "idx_single_translation_status_updated",
                        columnList = "status, updated_at"
                )
        }
)
public class SingleTranslationJobEntity {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private String jobId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(nullable = false, length = 50)
    private String translator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SingleTranslationJobStatus status;

    @Column(length = 50)
    private String stage;

    @Column(name = "current_step", nullable = false)
    private int currentStep;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps;

    @Column(name = "progress_detail", length = 2000)
    private String progressDetail;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "input_path", length = 2000)
    private String inputPath;

    @Column(name = "output_path", length = 2000)
    private String outputPath;

    private Integer width;

    private Integer height;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SingleTranslationJobEntity() {
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }

    public String getTranslator() {
        return translator;
    }

    public void setTranslator(String translator) {
        this.translator = translator;
    }

    public SingleTranslationJobStatus getStatus() {
        return status;
    }

    public void setStatus(SingleTranslationJobStatus status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public String getProgressDetail() {
        return progressDetail;
    }

    public void setProgressDetail(String progressDetail) {
        this.progressDetail = progressDetail;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getInputPath() {
        return inputPath;
    }

    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
