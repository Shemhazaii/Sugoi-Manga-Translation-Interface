package com.shemhazai.translationservice.translation.batch;

public enum BatchTranslationStatus {
    QUEUED,
    SUBMITTED,
    TRANSLATING,
    PACKAGING,
    COMPLETED,
    FAILED
}