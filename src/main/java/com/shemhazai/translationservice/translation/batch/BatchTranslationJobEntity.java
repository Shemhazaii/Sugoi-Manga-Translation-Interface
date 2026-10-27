package com.shemhazai.translationservice.translation.batch;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "batch_translation_jobs",
        indexes = {
                @Index(
                        name = "idx_batch_translation_status_updated",
                        columnList = "status, updated_at"
                )
        }
)
public class BatchTranslationJobEntity {

    @Id
    @Column(name = "job_id", nullable = false, updatable = false)
    private String jobId;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "source_lang", nullable = false, length = 20)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 20)
    private String targetLang;

    @Column(nullable = false, length = 50)
    private String translator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchTranslationStatus status;

    @Column(name = "input_archive", nullable = false, length = 2000)
    private String inputArchive;

    @Column(name = "input_folder", nullable = false, length = 2000)
    private String inputFolder;

    @Column(name = "output_folder", nullable = false, length = 2000)
    private String outputFolder;

    @Column(name = "result_archive", length = 2000)
    private String resultArchive;

    @Column(name = "total_images", nullable = false)
    private int totalImages;

    @Column(name = "completed_images", nullable = false)
    private int completedImages;

    @Column(name = "failed_images", nullable = false)
    private int failedImages;

    @Column(name = "current_image_index")
    private Integer currentImageIndex;

    @Column(name = "current_image_path", length = 2000)
    private String currentImagePath;

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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BatchTranslationJobEntity() {
    }

    public String getJobId() { return jobId; }
    public void setJobId(String value) { this.jobId = value; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }

    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String value) { this.sourceLang = value; }

    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String value) { this.targetLang = value; }

    public String getTranslator() { return translator; }
    public void setTranslator(String value) { this.translator = value; }

    public BatchTranslationStatus getStatus() { return status; }
    public void setStatus(BatchTranslationStatus value) { this.status = value; }

    public String getInputArchive() { return inputArchive; }
    public void setInputArchive(String value) { this.inputArchive = value; }

    public String getInputFolder() { return inputFolder; }
    public void setInputFolder(String value) { this.inputFolder = value; }

    public String getOutputFolder() { return outputFolder; }
    public void setOutputFolder(String value) { this.outputFolder = value; }

    public String getResultArchive() { return resultArchive; }
    public void setResultArchive(String value) { this.resultArchive = value; }

    public int getTotalImages() { return totalImages; }
    public void setTotalImages(int value) { this.totalImages = value; }

    public int getCompletedImages() { return completedImages; }
    public void setCompletedImages(int value) { this.completedImages = value; }

    public int getFailedImages() { return failedImages; }
    public void setFailedImages(int value) { this.failedImages = value; }

    public Integer getCurrentImageIndex() { return currentImageIndex; }
    public void setCurrentImageIndex(Integer value) { this.currentImageIndex = value; }

    public String getCurrentImagePath() { return currentImagePath; }
    public void setCurrentImagePath(String value) { this.currentImagePath = value; }

    public String getStage() { return stage; }
    public void setStage(String value) { this.stage = value; }

    public int getCurrentStep() { return currentStep; }
    public void setCurrentStep(int value) { this.currentStep = value; }

    public int getTotalSteps() { return totalSteps; }
    public void setTotalSteps(int value) { this.totalSteps = value; }

    public String getProgressDetail() { return progressDetail; }
    public void setProgressDetail(String value) { this.progressDetail = value; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { this.errorMessage = value; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}