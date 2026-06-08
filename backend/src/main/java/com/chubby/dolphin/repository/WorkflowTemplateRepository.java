package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.WorkflowTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, String> {
}
