package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AiResponseCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AiResponseCacheRepository extends JpaRepository<AiResponseCache, String> {

    Optional<AiResponseCache> findByPromptHash(String promptHash);

    @Modifying
    @Transactional
    @Query("DELETE FROM AiResponseCache c WHERE c.expiresAt < :now")
    void deleteByExpiresAtBefore(LocalDateTime now);
}
