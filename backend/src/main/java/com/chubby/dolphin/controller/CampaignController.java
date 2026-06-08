package com.chubby.dolphin.controller;

import com.chubby.dolphin.entity.Campaign;
import com.chubby.dolphin.repository.CampaignRepository;
import com.chubby.dolphin.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignRepository repo;
    private final SecurityUtils sec;

    public CampaignController(CampaignRepository repo, SecurityUtils sec) {
        this.repo = repo;
        this.sec = sec;
    }

    /** List all campaigns for the logged-in account */
    @GetMapping
    public ResponseEntity<List<Campaign>> list() {
        return ResponseEntity.ok(repo.findByWorkspaceId(sec.currentWorkspaceId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Campaign> get(@PathVariable String id) {
        Optional<Campaign> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (!c.getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(c);
    }

    /** Create a new campaign */
    @PostMapping
    public ResponseEntity<Campaign> create(@RequestBody Campaign c) {
        c.setWorkspaceId(sec.currentWorkspaceId());
        c.setStatus("ACTIVE");
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(c));
    }

    /** Update campaign (budget, status, name etc) */
    @PutMapping("/{id}")
    public ResponseEntity<Campaign> update(@PathVariable String id, @RequestBody Campaign body) {
        Optional<Campaign> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (!c.getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        if (body.getName()     != null) c.setName(body.getName());
        if (body.getBudget()   != null) c.setBudget(body.getBudget());
        if (body.getObjective()!= null) c.setObjective(body.getObjective());
        if (body.getCtr()      != null) c.setCtr(body.getCtr());
        if (body.getCpl()      != null) c.setCpl(body.getCpl());
        if (body.getRoas()     != null) c.setRoas(body.getRoas());
        if (body.getSpent()    != null) c.setSpent(body.getSpent());
        if (body.getPerformanceScore() != null) c.setPerformanceScore(body.getPerformanceScore());
        c.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(c));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Campaign> pause(@PathVariable String id) {
        Optional<Campaign> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (!c.getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        c.setStatus("PAUSED");
        c.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(c));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Campaign> resume(@PathVariable String id) {
        Optional<Campaign> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (!c.getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        c.setStatus("ACTIVE");
        c.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(repo.save(c));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        Optional<Campaign> opt = repo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Campaign c = opt.get();
        if (!c.getWorkspaceId().equals(sec.currentWorkspaceId())) {
            return ResponseEntity.status(403).build();
        }
        repo.delete(c);
        return ResponseEntity.noContent().build();
    }
}
