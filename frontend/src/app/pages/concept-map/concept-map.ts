import {
  Component,
  OnInit,
  AfterViewInit,
  ChangeDetectionStrategy,
  inject,
  signal,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import cytoscape from 'cytoscape';
import { ConceptService } from '../../core/services/concept.service';
import type { ConceptGraphResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-concept-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatCardModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatTooltipModule,
  ],
  templateUrl: './concept-map.html',
  styleUrl: './concept-map.scss',
})
export class ConceptMapComponent implements OnInit, AfterViewInit {
  @ViewChild('cytoscapeContainer', { static: true }) container!: ElementRef<HTMLDivElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly conceptService = inject(ConceptService);
  private readonly snack = inject(MatSnackBar);

  readonly loading = signal(true);
  readonly nodeCount = signal(0);
  readonly edgeCount = signal(0);

  private cy?: cytoscape.Core;
  private kbId = '';

  ngOnInit() {
    this.kbId = this.route.snapshot.paramMap.get('kbId') ?? '';
  }

  ngAfterViewInit() {
    this.loadGraph();
  }

  loadGraph() {
    this.loading.set(true);
    this.conceptService.getGraph(this.kbId).subscribe({
      next: graph => this.renderGraph(graph),
      error: (err: Error) => {
        this.snack.open(err.message, 'Close', { duration: 4000 });
        this.loading.set(false);
      },
    });
  }

  private renderGraph(graph: ConceptGraphResponse) {
    this.nodeCount.set(graph.concepts.length);
    this.edgeCount.set(graph.edges.length);
    this.loading.set(false);

    const elements: cytoscape.ElementDefinition[] = [
      ...graph.concepts.map(n => ({
        data: { id: n.key, label: n.label, description: n.description },
      })),
      ...graph.edges.map((e, i) => ({
        data: {
          id: `e${i}`,
          source: e.source,
          target: e.target,
          label: e.relation,
        },
      })),
    ];

    this.cy = cytoscape({
      container: this.container.nativeElement,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'background-color': '#7c3aed',
            color: '#ffffff',
            'text-valign': 'center',
            'text-halign': 'center',
            'font-size': '12px',
            'font-weight': 600 as unknown as cytoscape.Css.FontWeight,
            padding: '10px',
            'min-zoomed-font-size': 8,
            'border-width': 2,
            'border-color': '#a78bfa',
          },
        },
        {
          selector: 'node:selected',
          style: { 'background-color': '#06b6d4', 'border-color': '#67e8f9' },
        },
        {
          selector: 'edge',
          style: {
            label: 'data(label)',
            'font-size': '10px',
            color: '#94a3b8',
            width: 1.5,
            'line-color': '#4c1d95',
            'target-arrow-color': '#4c1d95',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            'text-background-color': '#1e1b4b',
            'text-background-opacity': 0.8,
            'text-background-padding': '2px',
          },
        },
      ],
      layout: { name: 'cose', animate: false, padding: 30 } as cytoscape.LayoutOptions,
    });

    // Tooltip on hover
    this.cy.on('mouseover', 'node', evt => {
      const node = evt.target;
      node.style({ 'border-width': 4 });
    });
    this.cy.on('mouseout', 'node', evt => {
      evt.target.style({ 'border-width': 2 });
    });
  }

  fitView() {
    this.cy?.fit(undefined, 30);
  }

  zoomIn() {
    if (this.cy) this.cy.zoom(this.cy.zoom() * 1.25);
  }

  zoomOut() {
    if (this.cy) this.cy.zoom(this.cy.zoom() * 0.8);
  }
}
