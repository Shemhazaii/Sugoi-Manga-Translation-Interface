package com.shemhazai.translationservice.translation.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
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

}