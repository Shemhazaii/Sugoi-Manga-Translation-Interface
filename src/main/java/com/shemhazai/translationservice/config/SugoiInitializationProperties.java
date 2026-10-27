package com.shemhazai.translationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "sugoi.initialization")
public class SugoiInitializationProperties {

    private String backend = "offline";
    private String sourceLang = "ja";
    private String targetLang = "en";
    private Duration translatorTimeout = Duration.ofSeconds(30);
    private Duration mangaTimeout = Duration.ofSeconds(180);

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public String getTargetLang() {
        return targetLang;
    }

    public void setTargetLang(String targetLang) {
        this.targetLang = targetLang;
    }

    public Duration getTranslatorTimeout() {
        return translatorTimeout;
    }

    public void setTranslatorTimeout(Duration translatorTimeout) {
        this.translatorTimeout = translatorTimeout;
    }

    public Duration getMangaTimeout() {
        return mangaTimeout;
    }

    public void setMangaTimeout(Duration mangaTimeout) {
        this.mangaTimeout = mangaTimeout;
    }

}
