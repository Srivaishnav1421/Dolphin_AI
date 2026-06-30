package com.chubby.dolphin.service;

import java.io.InputStream;

/**
 * DA-052 — Abstraction layer for invoice PDF file storage.
 * Current implementation: LocalDiskStorageProvider.
 * Future-ready for S3, Cloudflare R2 or any object store.
 */
public interface FileStorageService {

    /**
     * Store a file from the given InputStream under the specified key.
     * @param fileKey  Logical file key / path (e.g. "invoices/CD-2627-0001.pdf")
     * @param data     InputStream of the file content
     * @param contentType MIME type of the content
     * @return Resolved storage path / URL to be persisted in the database
     */
    String store(String fileKey, InputStream data, String contentType);

    /**
     * Retrieve the absolute local file path or remote URL for the given key.
     */
    String resolve(String fileKey);

    /**
     * Delete a stored file.
     */
    void delete(String fileKey);
}
