package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.CompetitorAd;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetitorAdRepository extends JpaRepository<CompetitorAd, String> {
    List<CompetitorAd> findByWorkspaceId(String workspaceId);
    List<CompetitorAd> findByWorkspaceIdAndKeyword(String workspaceId, String keyword);
}
