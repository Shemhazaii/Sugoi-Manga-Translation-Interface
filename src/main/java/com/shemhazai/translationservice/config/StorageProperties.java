package com.shemhazai.translationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "translation.storage")
public record StorageProperties(String root) {
    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }
}

