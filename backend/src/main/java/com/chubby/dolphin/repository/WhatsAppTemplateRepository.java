package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WhatsAppTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, String> {
    List<WhatsAppTemplate> findByWorkspaceId(String workspaceId);
    Optional<WhatsAppTemplate> findByNameAndWorkspaceId(String name, String workspaceId);
    Optional<WhatsAppTemplate> findByNameAndIsSystemTrue(String name);
}
