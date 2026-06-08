package com.chubby.dolphin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workflow_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTemplate {

    @Id
    private String id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String workflowSnapshot;
}
