package com.shemhazai.translationservice.controllers;

import com.shemhazai.translationservice.sugoi.SugoiCommandService;
import com.shemhazai.translationservice.sugoi.SugoiInitializationService;
import com.shemhazai.translationservice.sugoi.SugoiStateService;
import com.shemhazai.translationservice.sugoi.SugoiWebsocketClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sugoi")
public class SugoiController {

    private final SugoiWebsocketClient client;
    private final SugoiStateService stateService;
    private final SugoiCommandService commandService;
    private final SugoiInitializationService initializationService;

    public SugoiController(SugoiWebsocketClient client, SugoiStateService stateService, SugoiCommandService commandService, SugoiInitializationService sugoiInitializationService, SugoiInitializationService initializationService) {
        this.client = client;
        this.stateService = stateService;
        this.commandService = commandService;
        this.initializationService = initializationService;
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connect() {
        try {
            client.connect().join();

            return ResponseEntity.ok(
                    Map.of(
                            "connected", client.isConnected()
                    )
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(
                            Map.of(
                                    "connected", false,
                                    "error", e.getMessage()
                            )
                    );
        }
    }

    @GetMapping("/connection")
    public Map<String, Object> connection() {
        return Map.of(
                "connected", client.isConnected()
        );
    }

    @PostMapping("/disconnect")
    public Map<String, Object> disconnect() {
        client.disconnect();

        return Map.of(
                "connected", false
        );
    }

    @PostMapping("/translator/status")
    public ResponseEntity<?> getTranslatorStatus() {

        commandService.getTranslatorStatus();

        return ResponseEntity.accepted().body(
                Map.of("sent", true)
        );
    }

    @PostMapping("/manga/status")
    public ResponseEntity<?> getMangaModuleStatus() {

        commandService.getMangaModuleStatus();

        return ResponseEntity.accepted().body(
                Map.of("sent", true)
        );
    }

    @PostMapping("/translator/load")
    public ResponseEntity<?> loadTranslator(
            @RequestParam String backend
    ) {
        commandService.loadTranslator(backend);

        return ResponseEntity.accepted().body(
                Map.of(
                        "sent", true,
                        "backend", backend
                )
        );
    }

    @PostMapping("/translator/activate")
    public ResponseEntity<?> activateTranslator(
            @RequestParam String backend
    ) {
        commandService.setTranslator(backend);

        return ResponseEntity.accepted().body(
                Map.of(
                        "sent", true,
                        "backend", backend
                )
        );
    }

    @PostMapping("/manga/load")
    public ResponseEntity<?> loadMangaModule(
            @RequestParam(defaultValue = "ja") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang
    ) {
        commandService.loadMangaModule(sourceLang, targetLang);

        return ResponseEntity.accepted().body(
                Map.of(
                        "sent", true,
                        "sourceLang", sourceLang,
                        "targetLang", targetLang
                )
        );
    }

    @GetMapping("/state")
    public Map<String, Object> getState() {
        return Map.of(
                "connected", client.isConnected(),
                "translatorStatus",
                stateService.getTranslatorStatus() == null
                        ? Map.of()
                        : stateService.getTranslatorStatus(),
                "mangaModuleStatus",
                stateService.getMangaModuleStatus() == null
                        ? Map.of()
                        : stateService.getMangaModuleStatus(),
                "mangaModuleProgress",
                stateService.getMangaModuleProgress() == null
                        ? Map.of()
                        : stateService.getMangaModuleProgress(),
                "mangaModuleReady",
                stateService.isMangaModuleReady()
        );
    }

    @GetMapping("/ready")
    public Map<String, Object> readiness() {
        return Map.of(
                "ready", initializationService.isReady()
        );
    }

}
