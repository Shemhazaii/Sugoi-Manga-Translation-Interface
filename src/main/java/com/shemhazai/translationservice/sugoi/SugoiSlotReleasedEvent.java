package com.shemhazai.translationservice.sugoi;

public record SugoiSlotReleasedEvent(
        String jobId,
        String jobType
) {
}
