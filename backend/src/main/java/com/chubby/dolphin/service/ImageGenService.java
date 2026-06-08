package com.chubby.dolphin.service;

public interface ImageGenService {
    /**
     * Generates a high-converting visual ad asset dynamically using generative AI models.
     * Returns an absolute image URL to be linked with the active creative.
     */
    String generateAdImage(String visualPrompt, String theme);
}
