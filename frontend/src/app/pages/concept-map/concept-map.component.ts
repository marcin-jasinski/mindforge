import { Component, OnInit, OnDestroy, ElementRef, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import type { Core, NodeSingular, ElementDefinition, EventObject } from 'cytoscape';
import { ApiService } from '../../core/services/api.service';
import { ConceptGraphResponse, ConceptNode, LessonDetail } from '../../core/models/api.models';

@Component({
  selector: 'app-concept-map',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './concept-map.component.html',
  styleUrl: './concept-map.component.css',
})
export class ConceptMapComponent implements OnInit, OnDestroy {
  @ViewChild('cyContainer', { static: true }) cyContainer!: ElementRef<HTMLDivElement>;

  private cy: Core | null = null;
  lessons = signal<LessonDetail[]>([]);
  selectedLesson = signal('');
  loading = signal(true);
  fullscreen = signal(false);

  selectedNode = signal<{ label: string; group: string; definition: string; relations: string[] } | null>(null);

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getLessons().subscribe({
      next: lessons => this.lessons.set(lessons),
      error: () => {},
    });
    this.loadGraph();
  }

  ngOnDestroy() {
    this.cy?.destroy();
  }

  onLessonChange(lesson: string) {
    this.selectedLesson.set(lesson);
    this.loadGraph();
  }

  loadGraph() {
    this.loading.set(true);
    this.selectedNode.set(null);

    const lesson = this.selectedLesson() || undefined;
    this.api.getConceptGraph(lesson).subscribe({
      next: async data => {
        await this.renderGraph(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  private async renderGraph(data: ConceptGraphResponse) {
    const { default: cytoscape } = await import('cytoscape');
    this.cy?.destroy();

    const elements: ElementDefinition[] = [
      ...data.nodes.map(n => ({
        data: { id: n.id, label: n.label, group: n.group, nodeColor: n.color },
      })),
      ...data.edges.map((e, i) => ({
        data: { id: `e${i}`, source: e.source, target: e.target, label: e.label, description: e.description },
      })),
    ];

    this.cy = cytoscape({
      container: this.cyContainer.nativeElement,
      elements,
      style: [
        {
          selector: 'node',
          style: {
            label: 'data(label)',
            'text-valign': 'center',
            'text-halign': 'center',
            'background-color': '#6366f1',
            color: '#fff',
            'text-outline-color': '#6366f1',
            'text-outline-width': 2,
            'font-size': '11px',
            width: 40,
            height: 40,
          },
        },
        {
          selector: 'node[group="related"]',
          style: { 'background-color': '#10b981', 'text-outline-color': '#10b981' },
        },
        {
          selector: 'edge',
          style: {
            label: 'data(label)',
            'font-size': '9px',
            color: '#9ca3af',
            'line-color': '#d1d5db',
            'target-arrow-color': '#d1d5db',
            'target-arrow-shape': 'triangle',
            'curve-style': 'bezier',
            width: 1.5,
          },
        },
        {
          selector: 'node:selected',
          style: { 'border-width': 3, 'border-color': '#f59e0b' },
        },
      ],
      layout: { name: 'cose', animate: true, animationDuration: 500, nodeDimensionsIncludeLabels: true } as any,
      minZoom: 0.2,
      maxZoom: 3,
    });

    this.cy.on('tap', 'node', (evt: EventObject) => {
      const node = evt.target as NodeSingular;
      const neighbors = node.connectedEdges().map((e: any) => {
        const src = e.source().data('label');
        const tgt = e.target().data('label');
        const label = e.data('label');
        return `${src} —[${label}]→ ${tgt}`;
      });
      this.selectedNode.set({
        label: node.data('label'),
        group: node.data('group'),
        definition: '',
        relations: neighbors,
      });
    });

    this.cy.on('tap', (evt: EventObject) => {
      if (evt.target === this.cy) this.selectedNode.set(null);
    });
  }

  toggleFullscreen() {
    this.fullscreen.update(v => !v);
    setTimeout(() => this.cy?.resize(), 100);
  }

  fitGraph() {
    this.cy?.fit(undefined, 30);
  }
}
