
package com.shemhazai.translationservice.controllers;

import com.shemhazai.translationservice.translation.batch.*;
import com.shemhazai.translationservice.translation.single.SingleTranslationJob;
import com.shemhazai.translationservice.translation.single.SingleTranslationJobResponse;
import com.shemhazai.translationservice.translation.single.SingleTranslationJobStatus;
import com.shemhazai.translationservice.translation.single.SingleTranslationResult;
import com.shemhazai.translationservice.translation.single.SingleTranslationService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/translations")
public class TranslationController {

    private final SingleTranslationService translationService;

    private final BatchTranslationService batchTranslationService;
    private final BatchTranslationJobRegistry batchJobRegistry;


    public TranslationController(
            SingleTranslationService translationService, BatchTranslationService batchTranslationService, BatchTranslationJobRegistry batchJobRegistry
    ) {
        this.translationService = translationService;
        this.batchTranslationService = batchTranslationService;
        this.batchJobRegistry = batchJobRegistry;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ja") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang,
            @RequestParam(defaultValue = "offline") String translator
    ) {
        try {
            SingleTranslationJob job = translationService.submit(
                    file,
                    sourceLang,
                    targetLang,
                    translator
            );

            SingleTranslationJobResponse response =
                    SingleTranslationJobResponse.from(job);

            return ResponseEntity
                    .accepted()
                    .header(HttpHeaders.LOCATION, response.statusUrl())
                    .body(response);

        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", exception.getMessage())
            );
        } catch (Exception exception) {
            return ResponseEntity.status(
                    HttpStatus.INTERNAL_SERVER_ERROR
            ).body(Map.of(
                    "error", "Failed to create translation job"
            ));
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getStatus(
            @PathVariable String jobId
    ) {
        SingleTranslationJob job = translationService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                SingleTranslationJobResponse.from(job)
        );
    }

    @GetMapping("/{jobId}/result")
    public ResponseEntity<?> getResult(
            @PathVariable String jobId
    ) {
        SingleTranslationJob job = translationService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() == SingleTranslationJobStatus.FAILED) {
            return ResponseEntity.unprocessableContent().body(
                    Map.of(
                            "jobId", jobId,
                            "status", job.getStatus().name(),
                            "error", job.getErrorMessage() == null
                                    ? "Translation failed"
                                    : job.getErrorMessage()
                    )
            );
        }

        if (job.getStatus() != SingleTranslationJobStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    Map.of(
                            "jobId", jobId,
                            "status", job.getStatus().name(),
                            "message", "Translation is not complete yet"
                    )
            );
        }
        SingleTranslationResult result =
                translationService.getResult(jobId);

        final byte[] image;

        try {
            image = translationService.getResultImage(jobId);
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", exception.getMessage()));
        }

        if (image == null) {
            return ResponseEntity.status(
                    HttpStatus.BAD_GATEWAY
            ).body(Map.of(
                    "error", "Translation result image is missing"
            ));

        }


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setContentLength(image.length);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("translated.jpg", StandardCharsets.UTF_8)
                        .build()
        );
        headers.set("X-Translation-Job-Id", result.jobId());
        headers.set("X-Image-Width", Integer.toString(result.width()));
        headers.set("X-Image-Height", Integer.toString(result.height()));

        return new ResponseEntity<>(
                image,
                headers,
                HttpStatus.OK
        );
    }


    @PostMapping(
            path = "/batch",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> submitBatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "ja") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang,
            @RequestParam(defaultValue = "offline") String translator
    ) {
        try {
            BatchTranslationJob job = batchTranslationService.submit(
                    file, sourceLang, targetLang, translator
            );

            BatchTranslationJobResponse response =
                    BatchTranslationJobResponse.from(job);

            return ResponseEntity.accepted()
                    .header(HttpHeaders.LOCATION, response.statusUrl())
                    .body(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Failed to submit batch translation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    java.util.Map.of(
                            "error", "Failed to create batch translation job"
                    )
            );
        }
    }

    @GetMapping("/batch/{jobId}")
    public ResponseEntity<?> getBatchStatus(
            @PathVariable String jobId
    ) {
        BatchTranslationJob job = batchJobRegistry.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(BatchTranslationJobResponse.from(job));
    }

    @GetMapping("/batch/{jobId}/result")
    public ResponseEntity<?> getBatchResult(
            @PathVariable String jobId
    ) {
        BatchTranslationJob job = batchJobRegistry.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        if (job.getStatus() == BatchTranslationStatus.FAILED) {
            return ResponseEntity.unprocessableContent().body(
                    java.util.Map.of(
                            "jobId", jobId,
                            "status", job.getStatus().name(),
                            "error", job.getErrorMessage() == null
                                    ? "Batch translation failed"
                                    : job.getErrorMessage()
                    )
            );
        }

        if (job.getStatus() != BatchTranslationStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    java.util.Map.of(
                            "jobId", jobId,
                            "status", job.getStatus().name(),
                            "message", "Batch translation is not complete yet"
                    )
            );
        }

        Path archive = job.getResultArchive();

        if (archive == null || !Files.isRegularFile(archive)) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    java.util.Map.of(
                            "error", "Batch result archive is missing"
                    )
            );
        }

        try {
            byte[] bytes = Files.readAllBytes(archive);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .contentLength(bytes.length)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename("translated-" + jobId + ".zip")
                                    .build()
                                    .toString()
                    )
                    .body(bytes);

        } catch (IOException e) {
            log.error("Failed to read batch result archive: {}", jobId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                    java.util.Map.of("error", "Failed to read result archive")
            );
        }
    }

}
