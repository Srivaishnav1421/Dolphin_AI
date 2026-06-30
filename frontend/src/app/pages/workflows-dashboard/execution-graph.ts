import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface GraphNode {
  id: string;
  label: string;
  agent: string;
  status: string;
  duration?: string;
  x: number;
  y: number;
}

export interface GraphEdge {
  fromX: number;
  fromY: number;
  toX: number;
  toY: number;
  status: string;
}

@Component({
  selector: 'app-execution-graph',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="graph-viewport">
      <svg class="graph-svg" width="100%" height="340">
        <defs>
          <!-- Curved path markers/arrowheads -->
          <marker id="arrow-completed" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#10B981"/>
          </marker>
          <marker id="arrow-running" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#6366F1"/>
          </marker>
          <marker id="arrow-failed" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#EF4444"/>
          </marker>
          <marker id="arrow-pending" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#27272A"/>
          </marker>
        </defs>

        <!-- Edges/Connections -->
        <path
          *ngFor="let edge of edges"
          [attr.d]="getConnectionPath(edge)"
          [class]="'graph-edge graph-edge--' + edge.status.toLowerCase()"
          [attr.marker-end]="'url(#arrow-' + edge.status.toLowerCase() + ')'"
        />

        <!-- Nodes -->
        <g *ngFor="let node of nodes"
           [attr.transform]="'translate(' + (node.x - 70) + ',' + (node.y - 40) + ')'"
           (click)="selectNode(node.id)"
           class="graph-node-group"
           [class.graph-node-group--active]="node.id === activeNodeId">
          
          <!-- Node Container Card -->
          <rect
            width="150"
            height="70"
            rx="8"
            [class]="'graph-node-rect graph-node-rect--' + node.status.toLowerCase()"
          />
          
          <!-- Node Icon badge/indicator -->
          <circle cx="20" cy="20" r="10" [class]="'node-indicator node-indicator--' + node.status.toLowerCase()"/>
          <text x="20" y="24" class="node-icon-text">🤖</text>

          <!-- Labels -->
          <text x="40" y="24" class="node-title">{{ node.label | slice:0:14 }}</text>
          <text x="40" y="42" class="node-desc">{{ node.agent || 'Router' }}</text>
          <text x="40" y="56" class="node-subdesc" *ngIf="node.duration">{{ node.duration }}</text>
        </g>
      </svg>
    </div>
  `,
  styles: [`
    @use '../../../styles/design-tokens.scss' as *;

    .graph-viewport {
      width: 100%;
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid $border-color;
      border-radius: $radius-lg;
      overflow: hidden;
      position: relative;
    }

    .graph-svg {
      display: block;
    }

    .graph-edge {
      fill: none;
      stroke-width: 2px;
      transition: stroke $transition-fast;
      
      &--completed { stroke: $color-success; }
      &--running { stroke: $accent-primary; stroke-dasharray: 4; animation: dash 1s linear infinite; }
      &--failed { stroke: $color-danger; }
      &--pending { stroke: $border-color; }
      &--waiting_for_approval { stroke: $color-warning; }
    }

    @keyframes dash {
      to { stroke-dashoffset: -20; }
    }

    .graph-node-group {
      cursor: pointer;
      &:hover .graph-node-rect {
        filter: brightness(1.15);
      }
    }

    .graph-node-group--active .graph-node-rect {
      stroke: $accent-primary;
      stroke-width: 2px;
      filter: drop-shadow(0 0 6px rgba(99, 102, 241, 0.4));
    }

    .graph-node-rect {
      fill: $bg-surface;
      stroke: $border-color;
      stroke-width: 1px;
      transition: all $transition-fast;
      
      &--completed { stroke: $color-success; }
      &--running { stroke: $accent-primary; }
      &--failed { stroke: $color-danger; }
      &--waiting_for_approval { stroke: $color-warning; }
    }

    .node-indicator {
      fill: $border-color;
      &--completed { fill: rgba(16, 185, 129, 0.2); }
      &--running { fill: rgba(99, 102, 241, 0.2); }
      &--failed { fill: rgba(239, 68, 68, 0.2); }
      &--waiting_for_approval { fill: rgba(245, 158, 11, 0.2); }
    }

    .node-icon-text {
      font-size: 11px;
      text-anchor: middle;
    }

    .node-title {
      font-family: $font-family;
      font-size: 11px;
      font-weight: 600;
      fill: $text-primary;
    }

    .node-desc {
      font-family: $font-family;
      font-size: 10px;
      fill: $text-secondary;
    }

    .node-subdesc {
      font-family: $font-family;
      font-size: 9px;
      fill: $text-muted;
    }
  `]
})
export class ExecutionGraph implements OnChanges {
  @Input() steps: any[] = [];
  @Input() activeNodeId: string | null = null;
  @Output() nodeSelected = new EventEmitter<string>();

  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['steps']) {
      this.buildGraph();
    }
  }

  buildGraph() {
    if (!this.steps || this.steps.length === 0) {
      this.nodes = [];
      this.edges = [];
      return;
    }

    // Lay out nodes in a linear left-to-right chain with custom spacing offsets
    const spacingX = 220;
    const startX = 100;
    const posY = 170;

    this.nodes = this.steps.map((step, idx) => {
      return {
        id: step.id,
        label: step.workflowName,
        agent: step.agentUsed || 'Router',
        status: step.status || 'PENDING',
        duration: step.executionDuration ? `${(step.executionDuration / 1000).toFixed(1)}s` : undefined,
        x: startX + idx * spacingX,
        y: posY + (idx % 2 === 0 ? -30 : 30) // Subtle staggered wave layout for better visibility
      };
    });

    // Build edges/connections between consecutive steps
    this.edges = [];
    for (let i = 0; i < this.nodes.length - 1; i++) {
      const fromNode = this.nodes[i];
      const toNode = this.nodes[i + 1];
      this.edges.push({
        fromX: fromNode.x + 80, // Right center of card
        fromY: fromNode.y,
        toX: toNode.x - 80,    // Left center of next card
        toY: toNode.y,
        status: toNode.status
      });
    }
  }

  getConnectionPath(edge: GraphEdge): string {
    const cpX1 = edge.fromX + 60;
    const cpY1 = edge.fromY;
    const cpX2 = edge.toX - 60;
    const cpY2 = edge.toY;
    return `M ${edge.fromX} ${edge.fromY} C ${cpX1} ${cpY1}, ${cpX2} ${cpY2}, ${edge.toX} ${edge.toY}`;
  }

  selectNode(id: string) {
    this.nodeSelected.emit(id);
  }
}
