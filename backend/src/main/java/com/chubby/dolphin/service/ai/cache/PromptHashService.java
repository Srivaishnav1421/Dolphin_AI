package com.chubby.dolphin.service.ai.cache;

import com.chubby.dolphin.dto.ai.LlmRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class PromptHashService {

    /**
     * Computes SHA-256 hash of normal text.
     */
    public String hash(String text) {
        if (text == null) {
            return "";
        }
        // Normalize text: trim and handle casing/spacing differences uniformly
        String normalized = text.trim().toLowerCase().replaceAll("\\s+", " ");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 hashing algorithm not available", e);
        }
    }

    /**
     * Produces a deterministic hash combining request prompt and system prompt.
     */
    public String hashRequest(LlmRequest request) {
        if (request == null) {
            return "";
        }
        String systemPrompt = request.getSystemPrompt() != null ? request.getSystemPrompt() : "";
        String prompt = request.getPrompt() != null ? request.getPrompt() : "";
        return hash(systemPrompt + "||" + prompt);
    }
}
