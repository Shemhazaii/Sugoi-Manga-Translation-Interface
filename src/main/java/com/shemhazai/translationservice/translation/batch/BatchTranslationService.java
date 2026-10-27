
package com.shemhazai.translationservice.translation.batch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class BatchTranslationService {

    private static final Set<String> SUPPORTED_IMAGES = Set.of(
            "jpg", "jpeg", "png", "webp", "bmp"
    );

    private static final int MAX_ENTRIES = 10_000;
    private static final long MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;

    private final Path storageRoot;
    private final BatchTranslationJobRegistry registry;

    public BatchTranslationService(
            @Value("${translation.storage-root:/mnt/sugoi-files}")
            String storageRoot,
            BatchTranslationJobRegistry registry
    ) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.registry = registry;
    }

    public BatchTranslationJob submit(
            MultipartFile file,
            String sourceLang,
            String targetLang,
            String translator
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("ZIP file must not be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("Only ZIP archives are supported");
        }

        if (sourceLang.isBlank() || targetLang.isBlank()
                || translator.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceLang, targetLang, and translator must not be blank"
            );
        }

        String jobId = UUID.randomUUID().toString();

        Path uploadDir = storageRoot.resolve("uploads").resolve(jobId);
        Path inputArchive = uploadDir.resolve("input.zip");
        Path inputFolder = uploadDir.resolve("extracted");
        Path outputFolder = storageRoot.resolve("results")
                .resolve(jobId).resolve("translated");

        try {
            Files.createDirectories(inputFolder);
            Files.createDirectories(outputFolder);
            Files.createDirectories(inputArchive.getParent());

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, inputArchive, StandardCopyOption.REPLACE_EXISTING);
            }

            extractArchive(inputArchive, inputFolder);

            boolean containsImage;
            try (var paths = Files.walk(inputFolder)) {
                containsImage = paths
                        .filter(Files::isRegularFile)
                        .anyMatch(this::isSupportedImage);
            }

            if (!containsImage) {
                throw new IllegalArgumentException(
                        "ZIP archive does not contain supported images"
                );
            }

            BatchTranslationJob job = new BatchTranslationJob(
                    jobId,
                    Path.of(originalFilename).getFileName().toString(),
                    sourceLang,
                    targetLang,
                    translator,
                    inputArchive,
                    inputFolder,
                    outputFolder
            );

            registry.register(job);
            return job;

        } catch (Exception e) {
            deleteDirectoryQuietly(uploadDir);
            deleteDirectoryQuietly(outputFolder.getParent());

            if (e instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to create batch translation job", e);
        }
    }

    private void extractArchive(Path archive, Path destination)
            throws IOException {
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        int entries = 0;
        long expandedBytes = 0;

        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(archive))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IllegalArgumentException(
                            "ZIP archive contains too many entries"
                    );
                }

                Path target = normalizedDestination.resolve(entry.getName())
                        .normalize();

                if (!target.startsWith(normalizedDestination)) {
                    throw new IllegalArgumentException(
                            "ZIP archive contains an unsafe path"
                    );
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    zip.closeEntry();
                    continue;
                }

                Files.createDirectories(target.getParent());

                try (var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = zip.read(buffer)) != -1) {
                        expandedBytes += count;
                        if (expandedBytes > MAX_EXPANDED_BYTES) {
                            throw new IllegalArgumentException(
                                    "Expanded ZIP exceeds the 2 GiB limit"
                            );
                        }
                        output.write(buffer, 0, count);
                    }
                }

                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("Invalid ZIP archive", e);
        }
    }

    private boolean isSupportedImage(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return false;
        }
        return SUPPORTED_IMAGES.contains(
                filename.substring(dot + 1).toLowerCase(Locale.ROOT)
        );
    }

    private void deleteDirectoryQuietly(Path directory) {
        try {
            if (directory != null && Files.exists(directory)) {
                try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(
                            java.util.Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
        } catch (IOException ignored) {
            // Preserve the original submission error.
        }
    }
}
