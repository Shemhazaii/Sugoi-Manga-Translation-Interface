package com.shemhazai.translationservice.controllers;

import com.shemhazai.translationservice.config.SugoiProperties;
import com.shemhazai.translationservice.storage.StorageStatus;
import com.shemhazai.translationservice.storage.TranslationStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final SugoiProperties sugoiProperties;
    private final TranslationStorageService storageService;

    public HealthController(SugoiProperties sugoiProperties, TranslationStorageService storageService) {
        this.sugoiProperties = sugoiProperties;
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        StorageStatus storage = storageService.inspect();
        URI uri = URI.create(sugoiProperties.websocketUrl());
        return Map.of(
                "service", "translation-service",
                "status", "UP",
                "sugoiConfigured", true,
                "sugoiEndpoint", uri.getScheme() + "://" + uri.getAuthority() + uri.getPath(),
                "storage", storage
        );
    }
}