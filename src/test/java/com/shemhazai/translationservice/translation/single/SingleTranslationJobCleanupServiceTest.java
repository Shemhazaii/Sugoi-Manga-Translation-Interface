
package com.shemhazai.translationservice.translation.single;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SingleTranslationJobCleanupServiceTest {

    private static final String JOB_ID =
            "translation-28cfc76f-2813-460e-8ee8-0db98157f83a";

    @TempDir
    Path tempDir;

    private SingleTranslationJobRegistry registry;
    private SingleTranslationPersistenceService persistence;
    private SingleTranslationJobCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        registry = mock(SingleTranslationJobRegistry.class);
        persistence = mock(SingleTranslationPersistenceService.class);

        cleanupService = new SingleTranslationJobCleanupService(
                registry,
                persistence,
                tempDir.toString(),
                Duration.ofDays(7)
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = SingleTranslationJobStatus.class,
            names = {"COMPLETED", "FAILED"}
    )
    void shouldDeleteExpiredTerminalJob(SingleTranslationJobStatus status)
            throws IOException {

        SingleTranslationJobEntity entity =
                mock(SingleTranslationJobEntity.class);

        when(entity.getJobId()).thenReturn(JOB_ID);
        when(persistence.findExpiredTerminalJobs(any(Instant.class)))
                .thenReturn(List.of(entity));

        Path uploadDirectory =
                Files.createDirectories(tempDir.resolve("uploads").resolve(JOB_ID));
        Path resultDirectory =
                Files.createDirectories(tempDir.resolve("results").resolve(JOB_ID));

        Files.writeString(uploadDirectory.resolve("input.jpg"), "test input");
        Files.writeString(resultDirectory.resolve("translated.jpg"), "test result");

        cleanupService.cleanupExpiredJobs();

        assertFalse(Files.exists(uploadDirectory));
        assertFalse(Files.exists(resultDirectory));

        verify(persistence).deleteExpiredJob(
                eq(JOB_ID),
                any(Instant.class)
        );
        verify(registry).evict(JOB_ID);
    }

    @Test
    void shouldNotDeleteAnythingWhenThereAreNoExpiredJobs() {
        when(persistence.findExpiredTerminalJobs(any(Instant.class)))
                .thenReturn(List.of());

        cleanupService.cleanupExpiredJobs();

        verify(persistence, never())
                .deleteExpiredJob(anyString(), any(Instant.class));
        verify(registry, never()).evict(anyString());
    }
}
