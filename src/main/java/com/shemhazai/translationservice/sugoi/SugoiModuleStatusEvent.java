package com.shemhazai.translationservice.sugoi;

import tools.jackson.databind.JsonNode;

public record SugoiModuleStatusEvent(JsonNode status) {
}
