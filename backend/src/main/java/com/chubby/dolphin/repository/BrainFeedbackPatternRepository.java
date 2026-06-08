package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.BrainFeedbackPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrainFeedbackPatternRepository extends JpaRepository<BrainFeedbackPattern, String> {
    List<BrainFeedbackPattern> findByProductIgnoreCase(String product);
}
