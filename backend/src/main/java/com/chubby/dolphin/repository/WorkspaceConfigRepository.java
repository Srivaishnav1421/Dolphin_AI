package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkspaceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkspaceConfigRepository extends JpaRepository<WorkspaceConfig, String> {
    Optional<WorkspaceConfig> findByWorkspaceId(String workspaceId);
    Optional<WorkspaceConfig> findByWhatsappVerifyTokenHash(String hash);

    default Optional<WorkspaceConfig> findByVerifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return findByWhatsappVerifyTokenHash(hexString.toString());
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
