package com.shemhazai.translationservice.storage;

import java.util.List;

public record StorageStatus(
        String configuredRoot,
        boolean exists,
        boolean directory,
        boolean readable,
        boolean writable,
        List<String> problems
) {
}

