package com.shemhazai.translationservice.translation.single;


public record SingleTranslationResult (
        String jobId,
        String filePath,
        String translatedImageBase64,
        String outputPath,
        int width,
        int height
){

}
