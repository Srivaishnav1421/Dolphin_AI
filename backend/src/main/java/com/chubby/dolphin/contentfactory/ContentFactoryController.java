package com.chubby.dolphin.contentfactory;

import com.chubby.dolphin.contentfactory.dto.ContentFactoryGenerateRequest;
import com.chubby.dolphin.rbac.Permission;
import com.chubby.dolphin.security.AccessControlService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/content-factory")
@RequiredArgsConstructor
public class ContentFactoryController {

    private final ContentFactoryService contentFactoryService;
    private final AccessControlService access;

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody ContentFactoryGenerateRequest request) {
        access.requireWorkspacePermission(Permission.CREATIVE_GENERATE);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(contentFactoryService.generate(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/items")
    public ResponseEntity<?> listItems() {
        access.requireWorkspacePermission(Permission.CREATIVE_READ);
        return ResponseEntity.ok(contentFactoryService.listItems());
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<?> getItem(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CREATIVE_READ);
        try {
            return ResponseEntity.ok(contentFactoryService.getItem(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/variants/{id}/submit-approval")
    public ResponseEntity<?> submitVariantForApproval(@PathVariable String id) {
        access.requireWorkspacePermission(Permission.CREATIVE_UPDATE);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(contentFactoryService.submitVariantForApproval(id));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
