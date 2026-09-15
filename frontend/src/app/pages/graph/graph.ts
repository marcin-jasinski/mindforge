import { Component, ElementRef, OnDestroy, afterNextRender, inject, input, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import type { Core } from 'cytoscape';

import { Graph } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-graph',
  standalone: true,
  template: `
    <p>Pojęcia i źródła połączone linkami. Przerywana obwódka to strona, której jeszcze nie ma.</p>
    <div #canvas class="canvas" role="img" aria-label="Graf stron i linków między nimi"></div>
  `,
  styles: `.canvas { height: 70vh; border: 1px solid var(--mat-sys-outline-variant); border-radius: 0.5rem; }`,
})
export class GraphView implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private cy: Core | null = null;

  readonly kbId = input.required<string>();

  constructor() {
    afterNextRender(() => {
      this.api.get<Graph>(`/knowledge-bases/${this.kbId()}/graph`).subscribe(async (graph) => {
        const cytoscape = (await import('cytoscape')).default;
        const live = new Set(graph.pages.map((page) => page.path));
        const dangling = [...new Set(graph.links.map((link) => link.targetPath))].filter((path) => !live.has(path));
        this.cy = cytoscape({
          container: this.canvas().nativeElement,
          elements: [
            ...graph.pages.map((page) => ({
              data: { id: page.path, label: page.title, kind: page.type === 'Concept' ? 'concept' : 'source' },
            })),
            ...dangling.map((path) => ({ data: { id: path, label: path, kind: 'dangling' } })),
            ...graph.links.map((link, i) => ({ data: { id: `e${i}`, source: link.sourcePath, target: link.targetPath } })),
          ],
          layout: { name: 'cose' },
          style: [
            { selector: 'node', style: { label: 'data(label)', 'font-size': 10, 'background-color': '#4f6ef7' } },
            { selector: 'node[kind = "source"]', style: { 'background-color': '#16a34a', shape: 'round-rectangle' } },
            { selector: 'node[kind = "dangling"]', style: { 'background-color': '#fff', 'border-width': 2, 'border-style': 'dashed' } },
            { selector: 'edge', style: { width: 1, 'curve-style': 'bezier', 'target-arrow-shape': 'triangle' } },
          ],
        });
        this.cy.on('tap', 'node', (event) => {
          const path = event.target.id() as string;
          if (live.has(path)) {
            this.router.navigate(['/kb', this.kbId(), 'pages', ...path.split('/')]);
          }
        });
      });
    });
  }

  ngOnDestroy(): void {
    this.cy?.destroy();
  }
}
