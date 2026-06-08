package com.chubby.dolphin.security;

import com.chubby.dolphin.entity.WorkspaceConfig;
import com.chubby.dolphin.repository.WorkspaceConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Secret Key Rotation Scheduler — Seamlessly transitions credentials to the latest
 * encryption key (AES-GCM-256) without service downtime.
 * Automatically decrypts all credentials using the fallback key/algorithm and re-encrypts
 * them with the new active key.
 */
@Component
@Slf4j
public class SecretKeyRotationScheduler {

    private final WorkspaceConfigRepository configRepo;

    public SecretKeyRotationScheduler(WorkspaceConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    /**
     * Run rotation job.
     * In production, this can be triggered on-demand or run weekly.
     * We schedule it to run weekly on Sundays at 3:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void rotateKeys() {
        log.info("🔐 Starting automated Secret Key Rotation Job...");
        try {
            List<WorkspaceConfig> configs = configRepo.findAll();
            int count = 0;
            
            for (WorkspaceConfig config : configs) {
                boolean modified = false;
                
                // Read transparently (decrypts using whichever key works)
                String decryptedToken = config.getWhatsappToken();
                String decryptedVerifyToken = config.getWhatsappVerifyToken();
                
                if (decryptedToken != null && !decryptedToken.isEmpty()) {
                    // Setting it again forces JPA converter to run convertToDatabaseColumn
                    // which uses the new/current primary encryption key.
                    config.setWhatsappToken(decryptedToken);
                    modified = true;
                }
                
                if (decryptedVerifyToken != null && !decryptedVerifyToken.isEmpty()) {
                    config.setWhatsappVerifyToken(decryptedVerifyToken);
                    modified = true;
                }
                
                if (modified) {
                    config.setUpdatedAt(LocalDateTime.now());
                    configRepo.save(config);
                    count++;
                }
            }
            
            log.info("✅ Secret Key Rotation completed successfully! Re-encrypted {} workspace configurations.", count);
        } catch (Exception e) {
            log.error("❌ Secret Key Rotation failed: {}", e.getMessage(), e);
        }
    }
}
