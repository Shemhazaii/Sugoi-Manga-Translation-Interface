
package com.shemhazai.translationservice.translation.single;

import com.shemhazai.translationservice.sugoi.SugoiCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SingleTranslationService {

    private final SingleTranslationPersistenceService persistence;
    private final SingleTranslationJobRegistry registry;
    private final Path storageRoot;

    public SingleTranslationService(
            SingleTranslationPersistenceService persistence, SingleTranslationJobRegistry registry,
            SugoiCommandService commandService,
            @Value("${translation.storage.root:/mnt/sugoi-files}")
            String storageRoot,
            @Value("${translation.single.timeout:5m}")
            Duration timeout
    ) {
        this.persistence = persistence;
        this.registry = registry;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Translation timeout must be positive"
            );
        }
    }

    public SingleTranslationJob submit(
            MultipartFile file,
            String sourceLang,
            String targetLang,
            String translator
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getAllowedExtension(originalFilename);
        String jobId = "translation-" + UUID.randomUUID();

        SingleTranslationJob job = new SingleTranslationJob(
                jobId,
                originalFilename == null ? "image" : originalFilename,
                sourceLang,
                targetLang,
                translator
        );

        Path jobDirectory = storageRoot
                .resolve("uploads")
                .resolve(jobId)
                .normalize();

        Path inputPath = jobDirectory
                .resolve("input" + extension)
                .normalize();

        if (!jobDirectory.startsWith(storageRoot)
                || !inputPath.startsWith(jobDirectory)) {
            throw new IllegalArgumentException("Invalid storage path");
        }

        try {
            Files.createDirectories(jobDirectory);
            file.transferTo(inputPath);

            job.setInputPath(inputPath);
            job.updateStatus(SingleTranslationJobStatus.QUEUED);

            // Register exactly once. The dispatcher sends the command.
            registry.register(job);

            log.info("Translation job queued: {}", jobId);
            return job;
        } catch (IOException | IllegalStateException exception) {
            deleteInputQuietly(inputPath);
            throw exception;
        } catch (RuntimeException exception) {
            deleteInputQuietly(inputPath);
            throw exception;
        }
    }

    public SingleTranslationJob getJob(String jobId) {
        return registry.getJob(jobId);
    }

    public SingleTranslationResult getResult(String jobId) {
        SingleTranslationJob job = registry.getJob(jobId);

        if (job == null) {
            return null;
        }

        return job.getResult();
    }

    public byte[] getResultImage(String jobId) throws IOException {
        SingleTranslationJob job = getJob(jobId);

        if (job == null
                || job.getStatus() != SingleTranslationJobStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Translation result is not available"
            );
        }

        return persistence.readResultImage(job);
    }



    private String getAllowedExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException(
                    "Image filename with extension is required"
            );
        }

        String normalized = filename.toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');

        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Image file extension is required"
            );
        }

        String extension = normalized.substring(dot);

        return switch (extension) {
            case ".jpg", ".jpeg", ".png", ".webp",
                 ".bmp", ".tif", ".tiff" -> extension;
            default -> throw new IllegalArgumentException(
                    "Unsupported image extension: " + extension
            );
        };
    }

    private void deleteInputQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original error.
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
