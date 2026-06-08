package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.AiWorkspaceBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiWorkspaceBudgetRepository extends JpaRepository<AiWorkspaceBudget, String> {

    Optional<AiWorkspaceBudget> findByWorkspaceId(String workspaceId);
}
