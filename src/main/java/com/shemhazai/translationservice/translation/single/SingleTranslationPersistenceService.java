package com.shemhazai.translationservice.translation.single;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class SingleTranslationPersistenceService {

    private final SingleTranslationJobJpaRepository repository;
    private final Path storageRoot;


    public SingleTranslationPersistenceService(
            SingleTranslationJobJpaRepository repository,
            @Value("${translation.storage.root:/mnt/sugoi-files}")
            String storageRoot
    ) {
        this.repository = repository;
        this.storageRoot = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional
    public void save(SingleTranslationJob job) {
        SingleTranslationJobEntity entity = repository
                .findById(job.getJobId())
                .orElseGet(SingleTranslationJobEntity::new);

        entity.setJobId(job.getJobId());
        entity.setOriginalFilename(job.getOriginalFilename());
        entity.setSourceLang(job.getSourceLang());
        entity.setTargetLang(job.getTargetLang());
        entity.setTranslator(job.getTranslator());
        entity.setStatus(job.getStatus());
        entity.setStage(job.getStage());
        entity.setCurrentStep(job.getCurrentStep());
        entity.setTotalSteps(job.getTotalSteps());
        entity.setProgressDetail(job.getProgressDetail());
        entity.setErrorMessage(job.getErrorMessage());
        entity.setInputPath(
                job.getInputPath() == null
                        ? null
                        : job.getInputPath().toString()
        );
        entity.setCreatedAt(job.getCreatedAt());
        entity.setUpdatedAt(job.getUpdatedAt());

        SingleTranslationResult result = job.getResult();

        if (result != null) {
            entity.setOutputPath(result.outputPath());
            entity.setWidth(result.width());
            entity.setHeight(result.height());
        }

        repository.save(entity);
    }

    /**
     * Persist the translated image before marking the job completed.
     */
    public SingleTranslationResult storeResult(
            SingleTranslationJob job,
            SingleTranslationResult result
    ) throws IOException {
        byte[] image;

        try {
            image = Base64.getDecoder().decode(
                    result.translatedImageBase64()
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Sugoi returned invalid Base64 image data",
                    exception
            );
        }

        Path resultDirectory = storageRoot
                .resolve("results")
                .resolve(job.getJobId())
                .normalize();

        Path outputPath = resultDirectory
                .resolve("translated.jpg")
                .normalize();

        if (!resultDirectory.startsWith(storageRoot)
                || !outputPath.startsWith(resultDirectory)) {
            throw new IOException("Invalid translation result path");
        }

        Files.createDirectories(resultDirectory);
        Files.write(outputPath, image);

        return new SingleTranslationResult(
                result.jobId(),
                result.filePath(),
                result.translatedImageBase64(),
                outputPath.toString(),
                result.width(),
                result.height()
        );
    }

    public Optional<SingleTranslationJobEntity> findEntity(
            String jobId
    ) {
        return repository.findById(jobId);
    }

    /**
     * Rebuilds job metadata from PostgreSQL.
     * The image Base64 is intentionally not loaded here.
     */
    public SingleTranslationJob restoreJob(
            SingleTranslationJobEntity entity
    ) {
        SingleTranslationJob job = new SingleTranslationJob(
                entity.getJobId(),
                entity.getOriginalFilename(),
                entity.getSourceLang(),
                entity.getTargetLang(),
                entity.getTranslator()
        );

        job.setInputPath(
                entity.getInputPath() == null
                        ? null
                        : Path.of(entity.getInputPath())
        );

        job.restoreMetadata(
                entity.getStatus(),
                entity.getUpdatedAt(),
                entity.getStage(),
                entity.getCurrentStep(),
                entity.getTotalSteps(),
                entity.getProgressDetail(),
                entity.getErrorMessage()
        );

        if (entity.getStatus()
                == SingleTranslationJobStatus.COMPLETED
                && entity.getOutputPath() != null) {
            job.setResult(new SingleTranslationResult(
                    entity.getJobId(),
                    null,
                    null,
                    entity.getOutputPath(),
                    entity.getWidth() == null ? 0 : entity.getWidth(),
                    entity.getHeight() == null ? 0 : entity.getHeight()
            ));
        }

        return job;
    }



    public byte[] readResultImage(SingleTranslationJob job)
            throws IOException {
        SingleTranslationResult result = job.getResult();


        if (result == null) {
            throw new IOException(
                    "Translation result metadata is missing for job: "
                            + job.getJobId()
            );
        }

        if (result.outputPath() == null
                || result.outputPath().isBlank()) {
            throw new IOException(
                    "Translation result outputPath is empty for job: "
                            + job.getJobId()
            );
        }

        Path outputPath = Path.of(result.outputPath())
                .toAbsolutePath()
                .normalize();

        Path resultsRoot = storageRoot.resolve("results")
                .toAbsolutePath()
                .normalize();

        if (!outputPath.startsWith(resultsRoot)) {
            throw new IOException(
                    "Result path is outside results directory. path="
                            + outputPath + ", root=" + resultsRoot
            );
        }

        if (!Files.exists(outputPath)) {
            throw new IOException(
                    "Result file does not exist: " + outputPath
            );
        }

        if (!Files.isRegularFile(outputPath)
                || !Files.isReadable(outputPath)) {
            throw new IOException(
                    "Result file is not a readable regular file: "
                            + outputPath
            );
        }
        return Files.readAllBytes(outputPath);
    }

    public Instant getUpdatedAt(String jobId) {
        return repository.findById(jobId)
                .map(SingleTranslationJobEntity::getUpdatedAt)
                .orElse(null);
    }

    public List<SingleTranslationJobEntity> findExpiredTerminalJobs(
            Instant cutoff
    ) {
        return repository.findByStatusInAndUpdatedAtBefore(
                List.of(
                        SingleTranslationJobStatus.COMPLETED,
                        SingleTranslationJobStatus.FAILED
                ),
                cutoff
        );
    }

    @Transactional
    public void deleteJob(String jobId) {
        repository.deleteById(jobId);
    }

    @Transactional
    public void deleteExpiredJob(String jobId, Instant cutoff) {
        List<SingleTranslationJobEntity> matches =
                repository.findByStatusInAndUpdatedAtBefore(
                        List.of(
                                SingleTranslationJobStatus.COMPLETED,
                                SingleTranslationJobStatus.FAILED
                        ),
                        cutoff
                );

        boolean stillExpired = matches.stream()
                .anyMatch(entity -> entity.getJobId().equals(jobId));

        if (stillExpired) {
            repository.deleteById(jobId);
        }
    }

    public Optional<SingleTranslationJobEntity> findNextQueuedJob() {
        return repository.findFirstByStatusOrderByCreatedAtAsc(
                SingleTranslationJobStatus.QUEUED
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverStuckJobs(ApplicationReadyEvent event) {
        List<SingleTranslationJobEntity> stuckJobs =
                repository.findByStatusIn(List.of(
                        SingleTranslationJobStatus.SUBMITTED,
                        SingleTranslationJobStatus.TRANSLATING
                ));

        stuckJobs.forEach(job -> {
            job.setStatus(SingleTranslationJobStatus.FAILED);
            job.setErrorMessage(
                    "Translation interrupted by application restart"
            );
        });

        repository.saveAll(stuckJobs);

        log.info(
                "Marked {} interrupted translation jobs as FAILED",
                stuckJobs.size()
        );
    }


}
