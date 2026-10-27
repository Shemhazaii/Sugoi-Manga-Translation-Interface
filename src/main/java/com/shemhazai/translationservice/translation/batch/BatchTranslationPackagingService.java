
package com.shemhazai.translationservice.translation.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
public class BatchTranslationPackagingService {

    private final BatchTranslationJobRegistry registry;

    public BatchTranslationPackagingService(
            BatchTranslationJobRegistry registry
    ) {
        this.registry = registry;
    }

    public void packageResult(BatchTranslationJob job) throws IOException {
        Path source = job.getOutputFolder().toAbsolutePath().normalize();
        Path archive = source.getParent().resolve("translated.zip");

        if (!Files.isDirectory(source)) {
            throw new IOException(
                    "Translated output directory does not exist: " + source
            );
        }

        Files.createDirectories(archive.getParent());

        Path temporary = archive.resolveSibling("translated.zip.tmp");

        try {
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(temporary))) {

                try (var paths = Files.walk(source)) {
                    for (Path path : paths.filter(Files::isRegularFile).toList()) {
                        String entryName = source.relativize(path)
                                .toString()
                                .replace('\\', '/');

                        zip.putNextEntry(new ZipEntry(entryName));

                        try (InputStream input = Files.newInputStream(path)) {
                            input.transferTo(zip);
                        }

                        zip.closeEntry();
                    }
                }
            }

            try {
                Files.move(
                        temporary,
                        archive,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(
                        temporary,
                        archive,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }

            if (!registry.packagingCompleted(job.getJobId(), archive)) {
                throw new IOException(
                        "Could not mark batch job as COMPLETED: " + job.getJobId()
                );
            }

            log.info(
                    "Batch result packaged: jobId={}, archive={}",
                    job.getJobId(),
                    archive
            );
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
