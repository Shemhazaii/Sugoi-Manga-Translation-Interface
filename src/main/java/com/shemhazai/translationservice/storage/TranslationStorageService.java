package com.shemhazai.translationservice.storage;


import com.shemhazai.translationservice.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Service
public class TranslationStorageService {
    private final StorageProperties properties;

    public TranslationStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    public StorageStatus inspect() {
        Path root = properties.rootPath();
        var problems = new ArrayList<String>();
        boolean exists = Files.exists(root);
        boolean directory = Files.isDirectory(root);
        boolean readable = Files.isReadable(root);
        boolean writable = Files.isWritable(root);

        if (!exists) problems.add("Storage root does not exist");
        if (exists && !directory) problems.add("Storage root is not a directory");
        if (exists && !readable) problems.add("Storage root is not readable");
        if (exists && !writable) problems.add("Storage root is not writable");

        return new StorageStatus(root.toString(), exists, directory, readable, writable, problems);
    }
}
