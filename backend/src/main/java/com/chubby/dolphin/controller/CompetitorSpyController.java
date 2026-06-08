package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.CompetitorAd;
import com.chubby.dolphin.repository.CompetitorAdRepository;
import com.chubby.dolphin.security.SecurityUtils;
import com.chubby.dolphin.service.CompetitorSpyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/competitor-spy")
@Slf4j
public class CompetitorSpyController {

    private final CompetitorSpyService spyService;
    private final CompetitorAdRepository adRepo;
    private final SecurityUtils sec;

    public CompetitorSpyController(CompetitorSpyService spyService,
                                   CompetitorAdRepository adRepo,
                                   SecurityUtils sec) {
        this.spyService = spyService;
        this.adRepo = adRepo;
        this.sec = sec;
    }

    /**
     * List analyzed competitor ads for this workspace, optionally filtered by keyword.
     */
    @GetMapping
    public ResponseEntity<List<CompetitorAd>> getCompetitorAds(@RequestParam(required = false) String keyword) {
        String workspaceId = sec.currentAccountId();
        if (keyword != null && !keyword.isBlank()) {
            return ResponseEntity.ok(adRepo.findByWorkspaceIdAndKeyword(workspaceId, keyword));
        }
        return ResponseEntity.ok(adRepo.findByWorkspaceId(workspaceId));
    }

    /**
     * Query active Meta Ad Library ads for a keyword, evaluate metrics and return them.
     */
    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','MANAGER')")
    public ResponseEntity<List<CompetitorAd>> triggerSpyQuery(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<CompetitorAd> ads = spyService.spyOnCompetitor(sec.currentAccountId(), keyword);
        return ResponseEntity.ok(ads);
    }
}
