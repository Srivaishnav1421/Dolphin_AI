package com.chubby.dolphin.service.ai;

import com.chubby.dolphin.dto.ai.LlmResponse;
import com.chubby.dolphin.entity.AiResponseCache;
import com.chubby.dolphin.entity.LlmProvider;
import com.chubby.dolphin.repository.AiResponseCacheRepository;
import com.chubby.dolphin.service.ai.cache.AiCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AiCacheServiceTest {

    @Mock
    private AiResponseCacheRepository cacheRepo;

    private AiCacheService cacheService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        cacheService = new AiCacheService(cacheRepo);
    }

    @Test
    public void testCacheHitReturnsEntryWhenUnexpired() {
        String hash = "abc123hash";
        AiResponseCache cacheEntry = AiResponseCache.builder()
                .promptHash(hash)
                .provider(LlmProvider.OLLAMA)
                .model("mistral:7b")
                .cachedResponse("I am cached")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(cacheRepo.findByPromptHash(hash)).thenReturn(Optional.of(cacheEntry));

        Optional<AiResponseCache> result = cacheService.get(hash);
        assertTrue(result.isPresent());
        assertEquals("I am cached", result.get().getCachedResponse());
        verify(cacheRepo, never()).delete(any(AiResponseCache.class));
    }

    @Test
    public void testCacheMissReturnsEmptyAndDeletesStaleRecordWhenExpired() {
        String hash = "abc123hash";
        AiResponseCache cacheEntry = AiResponseCache.builder()
                .promptHash(hash)
                .provider(LlmProvider.OLLAMA)
                .model("mistral:7b")
                .cachedResponse("I am expired")
                .expiresAt(LocalDateTime.now().minusHours(1)) // Expired 1 hour ago
                .build();

        when(cacheRepo.findByPromptHash(hash)).thenReturn(Optional.of(cacheEntry));

        Optional<AiResponseCache> result = cacheService.get(hash);
        assertTrue(result.isEmpty());
        verify(cacheRepo, times(1)).delete(cacheEntry);
    }

    @Test
    public void testPutSuccessfullySavesEntry() {
        String hash = "abc123hash";
        LlmResponse response = LlmResponse.builder()
                .content("Fresh content")
                .model("gpt-4o")
                .promptTokens(100)
                .completionTokens(50)
                .totalTokens(150)
                .build();

        cacheService.put(hash, response, LlmProvider.HUGGINGFACE, Duration.ofHours(24));

        verify(cacheRepo, times(1)).save(argThat(entry -> 
                hash.equals(entry.getPromptHash()) &&
                LlmProvider.HUGGINGFACE.equals(entry.getProvider()) &&
                "gpt-4o".equals(entry.getModel()) &&
                "Fresh content".equals(entry.getCachedResponse()) &&
                entry.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23))
        ));
    }

    @Test
    public void testCleanupExpiredCallsRepository() {
        cacheService.cleanupExpired();
        verify(cacheRepo, times(1)).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
}
