
package com.shemhazai.translationservice.translation.single;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
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

}
