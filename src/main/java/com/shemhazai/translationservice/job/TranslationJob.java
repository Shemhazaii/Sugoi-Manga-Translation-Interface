package com.shemhazai.translationservice.job;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "translation_jobs", indexes = {
        @Index(name = "idx_translation_jobs_status_created", columnList = "status, created_at")
})
@Getter
@Setter
public class TranslationJob {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TranslationJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TranslationJobStatus status;

    @Column(name = "source_lang", nullable = false, length = 16)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 16)
    private String targetLang;

    @Column(nullable = false, length = 32)
    private String translator;

    @Column(name = "source_path", length = 1024)
    private String sourcePath;

    @Column(name = "output_path", length = 1024)
    private String outputPath;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TranslationJob() {
    }

    public TranslationJob(UUID id, TranslationJobType type, String sourceLang, String targetLang, String translator) {
        this.id = id;
        this.type = type;
        this.status = TranslationJobStatus.QUEUED;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.translator = translator;
        this.progressPercent = 0;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }


    public void setProgressPercent(int progressPercent) {
        if (progressPercent < 0 || progressPercent > 100) throw new IllegalArgumentException("progressPercent must be 0..100");
        this.progressPercent = progressPercent;
    }
}
