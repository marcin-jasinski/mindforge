import {
  Component,
  OnInit,
  AfterViewInit,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  effect,
  ElementRef,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MfSnackbarService } from '../../core/services/mf-snackbar.service';
import { ThemeService } from '../../core/services/theme.service';
import cytoscape from 'cytoscape';
import { ConceptService } from '../../core/services/concept.service';
import type { ConceptGraphResponse } from '../../core/models/api.models';

interface ConceptNode {
  id: string;
  label: string;
  description?: string;
  lesson_id?: string;
}

interface ConceptRelation {
  id: string;
  type: string;
  target: string;
}

@Component({
  selector: 'app-concept-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  templateUrl: './concept-map.html',
  styleUrl: './concept-map.scss',
})
export class ConceptMapComponent implements OnInit, AfterViewInit {
  @ViewChild('graphContainer', { static: true }) container!: ElementRef<HTMLDivElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly conceptService = inject(ConceptService);
  private readonly snackbarService = inject(MfSnackbarService);
  private readonly themeService = inject(ThemeService);

  readonly loading = signal(true);
  readonly nodeCount = signal(0);
  readonly edgeCount = signal(0);
  readonly kbName = signal<string | null>(null);
  readonly selectedNode = signal<ConceptNode | null>(null);
  readonly nodeRelations = signal<ConceptRelation[]>([]);
  readonly isDark = computed(() => this.themeService.isDark());

  private cy?: cytoscape.Core;
  private kbId = '';
  private graphEdges: ConceptGraphResponse['edges'] = [];

  constructor() {
    effect(() => {
      const dark = this.isDark();
      if (!this.cy) return;
      this.cy.style([
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'background-color': dark ? '#7c3aed' : '#5b4fe9',
            color: '#ffffff',
            'text-valign': 'center',
            'text-halign': 'center',
            'font-size': '12px',
            'font-weight': 600 as unknown as cytoscape.Css.FontWeight,
            padding: '10px',
            'min-zoomed-font-size': 8,
            'border-width': 2,
            'border-color': dark ? '#a78bfa' : '#8b76f0',
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
            color: dark ? '#94a3b8' : '#64748b',
            width: 1.5,
            'line-color': dark ? '#4c1d95' : '#7c3aed',
            'target-arrow-color': dark ? '#4c1d95' : '#7c3aed',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            'text-background-color': dark ? '#1e1b4b' : '#f1f5f9',
            'text-background-opacity': 0.8,
            'text-background-padding': '2px',
          },
        },
      ]);
    });
  }

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
        this.snackbarService.show(err.message, 'error');
        this.loading.set(false);
      },
    });
  }

  private renderGraph(graph: ConceptGraphResponse) {
    this.nodeCount.set(graph.concepts.length);
    this.edgeCount.set(graph.edges.length);
    this.graphEdges = graph.edges;
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

    const dark = this.isDark();

    this.cy = cytoscape({
      container: this.container.nativeElement,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'background-color': dark ? '#7c3aed' : '#5b4fe9',
            color: '#ffffff',
            'text-valign': 'center',
            'text-halign': 'center',
            'font-size': '12px',
            'font-weight': 600 as unknown as cytoscape.Css.FontWeight,
            padding: '10px',
            'min-zoomed-font-size': 8,
            'border-width': 2,
            'border-color': dark ? '#a78bfa' : '#8b76f0',
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
            color: dark ? '#94a3b8' : '#64748b',
            width: 1.5,
            'line-color': dark ? '#4c1d95' : '#7c3aed',
            'target-arrow-color': dark ? '#4c1d95' : '#7c3aed',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            'text-background-color': dark ? '#1e1b4b' : '#f1f5f9',
            'text-background-opacity': 0.8,
            'text-background-padding': '2px',
          },
        },
      ],
      layout: { name: 'cose', animate: false, padding: 30 } as cytoscape.LayoutOptions,
    });

    // Tooltip on hover
    this.cy.on('mouseover', 'node', evt => {
      evt.target.style({ 'border-width': 4 });
    });
    this.cy.on('mouseout', 'node', evt => {
      evt.target.style({ 'border-width': 2 });
    });

    // Node selection panel
    this.cy.on('tap', 'node', evt => {
      const node = evt.target;
      const data = node.data();
      this.selectedNode.set({
        id: data.id,
        label: data.label,
        description: data.description,
        lesson_id: data.lesson_id,
      });
      // Compute relations for selected node
      const relations: ConceptRelation[] = this.graphEdges
        .filter(e => e.source === data.id)
        .map((e, i) => ({ id: `r${i}`, type: e.relation, target: e.target }));
      this.nodeRelations.set(relations);
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
