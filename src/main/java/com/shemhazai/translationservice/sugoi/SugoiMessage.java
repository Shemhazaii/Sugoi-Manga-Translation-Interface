package com.shemhazai.translationservice.sugoi;
import tools.jackson.databind.JsonNode;

public record SugoiMessage(
        String messageType,
        JsonNode payload
) {
}
