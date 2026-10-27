
package com.shemhazai.translationservice.translation.batch;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Service
public class BatchTranslationPersistenceService {

    private final BatchTranslationJobJpaRepository repository;

    public BatchTranslationPersistenceService(
            BatchTranslationJobJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public void save(BatchTranslationJob job) {
        BatchTranslationJobEntity entity = repository
                .findById(job.getJobId())
                .orElseGet(BatchTranslationJobEntity::new);

        entity.setJobId(job.getJobId());
        entity.setOriginalFilename(job.getOriginalFilename());
        entity.setSourceLang(job.getSourceLang());
        entity.setTargetLang(job.getTargetLang());
        entity.setTranslator(job.getTranslator());
        entity.setStatus(job.getStatus());

        entity.setInputArchive(job.getInputArchive().toString());
        entity.setInputFolder(job.getInputFolder().toString());
        entity.setOutputFolder(job.getOutputFolder().toString());

        entity.setResultArchive(
                job.getResultArchive() == null
                        ? null
                        : job.getResultArchive().toString()
        );

        entity.setTotalImages(job.getTotalImages());
        entity.setCompletedImages(job.getCompletedImages());
        entity.setFailedImages(job.getFailedImages());
        entity.setCurrentImageIndex(job.getCurrentImageIndex());
        entity.setCurrentImagePath(job.getCurrentImagePath());

        entity.setStage(job.getCurrentStage());
        entity.setCurrentStep(job.getCurrentStep());
        entity.setTotalSteps(job.getCurrentTotalSteps());
        entity.setProgressDetail(job.getProgressDetail());
        entity.setErrorMessage(job.getErrorMessage());

        entity.setCreatedAt(job.getCreatedAt());
        entity.setUpdatedAt(job.getUpdatedAt());

        repository.save(entity);
    }

    public Optional<BatchTranslationJobEntity> findEntity(
            String jobId
    ) {
        return repository.findById(jobId);
    }

    public Optional<BatchTranslationJobEntity> findNextQueuedJob() {
        return repository.findFirstByStatusOrderByCreatedAtAsc(
                BatchTranslationStatus.QUEUED
        );
    }

    public List<BatchTranslationJobEntity> findByStatuses(
            List<BatchTranslationStatus> statuses
    ) {
        return repository.findByStatusIn(statuses);
    }

    public BatchTranslationJob restoreJob(
            BatchTranslationJobEntity entity
    ) {
        BatchTranslationJob job = new BatchTranslationJob(
                entity.getJobId(),
                entity.getOriginalFilename(),
                entity.getSourceLang(),
                entity.getTargetLang(),
                entity.getTranslator(),
                Path.of(entity.getInputArchive()),
                Path.of(entity.getInputFolder()),
                Path.of(entity.getOutputFolder())
        );

        job.restoreMetadata(
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getTotalImages(),
                entity.getCompletedImages(),
                entity.getFailedImages(),
                entity.getCurrentImageIndex(),
                entity.getCurrentImagePath(),
                entity.getStage(),
                entity.getCurrentStep(),
                entity.getTotalSteps(),
                entity.getProgressDetail(),
                entity.getErrorMessage(),
                entity.getResultArchive() == null
                        ? null
                        : Path.of(entity.getResultArchive())
        );

        return job;
    }

    @Transactional
    public void save(BatchTranslationJobEntity entity) {
        repository.save(entity);
    }
}
