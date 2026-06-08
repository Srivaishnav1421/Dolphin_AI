package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.AuditLog;
import com.chubby.dolphin.repository.AuditLogRepository;
import com.chubby.dolphin.repository.BrainEventRepository;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.repository.LeadRepository;
import com.chubby.dolphin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogRepository auditRepo;
    private final UserRepository     userRepo;
    private final CampaignRepository campaignRepo;
    private final LeadRepository     leadRepo;
    private final BrainEventRepository brainEventRepo;

    public AdminController(AuditLogRepository auditRepo,
                           UserRepository userRepo,
                           CampaignRepository campaignRepo,
                           LeadRepository leadRepo,
                           BrainEventRepository brainEventRepo) {
        this.auditRepo = auditRepo;
        this.userRepo = userRepo;
        this.campaignRepo = campaignRepo;
        this.leadRepo = leadRepo;
        this.brainEventRepo = brainEventRepo;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLog>> auditLogs() {
        return ResponseEntity.ok(auditRepo.findTop100ByOrderByTimestampDesc());
    }

    @GetMapping("/users")
    public ResponseEntity<?> users() {
        return ResponseEntity.ok(userRepo.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(Map.of(
            "total_users",      userRepo.count(),
            "total_campaigns",  campaignRepo.count(),
            "total_leads",      leadRepo.count(),
            "total_events",     brainEventRepo.count()
        ));
    }
}
