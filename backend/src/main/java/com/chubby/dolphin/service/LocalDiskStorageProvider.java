package com.chubby.dolphin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * DA-052 — Local disk implementation of FileStorageService.
 * Files are stored under the configured base directory (default: storage/).
 * Replace this bean with an S3Provider or R2Provider for production deployments.
 */
@Service
@Slf4j
public class LocalDiskStorageProvider implements FileStorageService {

    @Value("${storage.local.base-dir:storage}")
    private String baseDir;

    @Override
    public String store(String fileKey, InputStream data, String contentType) {
        try {
            Path targetPath = Paths.get(baseDir, fileKey);
            Files.createDirectories(targetPath.getParent());
            Files.copy(data, targetPath, StandardCopyOption.REPLACE_EXISTING);
            String absolutePath = targetPath.toFile().getAbsolutePath();
            log.info("📁 File stored locally: key={}, path={}", fileKey, absolutePath);
            return absolutePath;
        } catch (IOException e) {
            log.error("❌ Failed to store file: key={}", fileKey, e);
            throw new RuntimeException("Storage failure for key: " + fileKey, e);
        }
    }

    @Override
    public String resolve(String fileKey) {
        return Paths.get(baseDir, fileKey).toFile().getAbsolutePath();
    }

    @Override
    public void delete(String fileKey) {
        File file = Paths.get(baseDir, fileKey).toFile();
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                log.info("🗑️ File deleted: key={}", fileKey);
            } else {
                log.warn("⚠️ Could not delete file: key={}", fileKey);
            }
        }
    }
}
