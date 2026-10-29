package com.shemhazai.translationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "sugoi.initialization")
public class SugoiInitializationProperties {

    private String backend = "offline";
    private String sourceLang = "ja";
    private String targetLang = "en";
    private Duration translatorTimeout = Duration.ofSeconds(30);
    private Duration mangaTimeout = Duration.ofSeconds(180);

}
