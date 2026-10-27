package com.shemhazai.translationservice.job;

public enum TranslationJobStatus {
    QUEUED,
    EXTRACTING,
    TRANSLATING,
    PACKAGING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}
