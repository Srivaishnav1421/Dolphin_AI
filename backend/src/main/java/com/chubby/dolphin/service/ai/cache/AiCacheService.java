package com.chubby.dolphin.service.ai.cache;

import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiResponseCache;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.repository.AiResponseCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class AiCacheService {

    private final AiResponseCacheRepository cacheRepo;

    public AiCacheService(AiResponseCacheRepository cacheRepo) {
        this.cacheRepo = cacheRepo;
    }

    /**
     * Retrieves a valid, unexpired cached prompt response.
     */
    public Optional<AiResponseCache> get(String promptHash) {
        try {
            Optional<AiResponseCache> cachedOpt = cacheRepo.findByPromptHash(promptHash);
            if (cachedOpt.isPresent()) {
                AiResponseCache cached = cachedOpt.get();
                if (cached.isExpired()) {
                    cacheRepo.delete(cached);
                    return Optional.empty();
                }
                return Optional.of(cached);
            }
        } catch (RuntimeException e) {
            log.warn("AI cache lookup skipped after storage error: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Caches an LLM completion response.
     */
    @Transactional
    public void put(String promptHash, LlmResponse response, LlmProvider provider, Duration ttl) {
        if (promptHash == null || response == null || provider == null) {
            return;
        }
        try {
            AiResponseCache cacheEntry = AiResponseCache.builder()
                    .promptHash(promptHash)
                    .provider(provider)
                    .model(response.getModel() != null ? response.getModel() : "unknown")
                    .cachedResponse(response.getContent())
                    .promptTokens(response.getPromptTokens() != null ? response.getPromptTokens() : 0)
                    .completionTokens(response.getCompletionTokens() != null ? response.getCompletionTokens() : 0)
                    .totalTokens(response.getTotalTokens() != null ? response.getTotalTokens() : 0)
                    .expiresAt(LocalDateTime.now().plus(ttl))
                    .build();
            cacheRepo.save(cacheEntry);
        } catch (RuntimeException e) {
            log.warn("AI cache write skipped after storage error: {}", e.getMessage());
        }
    }

    /**
     * Purges all expired caches.
     */
    @Transactional
    public void cleanupExpired() {
        cacheRepo.deleteByExpiresAtBefore(LocalDateTime.now());
    }
}
