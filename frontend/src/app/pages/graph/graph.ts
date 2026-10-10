import { Component, ElementRef, OnDestroy, afterNextRender, inject, input, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import type { Core } from 'cytoscape';

import { WikiApi } from '../../core/services/wiki-api';

const SMALL_GRAPH = 12;

@Component({
  selector: 'app-graph',
  standalone: true,
  template: `
    <p>Pojęcia i źródła połączone linkami. Przerywana obwódka to strona, której jeszcze nie ma.</p>
    <ul class="legend" aria-label="Legenda">
      <li><span class="swatch concept"></span> Pojęcie</li>
      <li><span class="swatch source"></span> Podsumowanie źródła</li>
      <li><span class="swatch dangling"></span> Brakująca strona</li>
    </ul>
    <div #canvas class="canvas" role="img" aria-label="Graf stron i linków między nimi"></div>
  `,
  styles: `
    .canvas { height: 70vh; border: 1px solid var(--mat-sys-outline-variant); border-radius: 0.5rem; }
    .legend { display: flex; flex-wrap: wrap; gap: 1rem; padding: 0; list-style: none; }
    .swatch { display: inline-block; width: 0.9rem; height: 0.9rem; vertical-align: middle; border-radius: 50%; }
    .swatch.concept { background: #4f6ef7; }
    .swatch.source { background: #16a34a; border-radius: 0.2rem; }
    .swatch.dangling { border: 2px dashed currentColor; box-sizing: border-box; }
  `,
})
export class GraphView implements OnDestroy {
  private readonly wiki = inject(WikiApi);
  private readonly router = inject(Router);
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private cy: Core | null = null;

  readonly kbId = input.required<string>();

  constructor() {
    afterNextRender(() => {
      this.wiki.graph(this.kbId()).subscribe(async (graph) => {
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
          // a force layout collapses a handful of nodes into a clump; a circle keeps every label readable
          layout: graph.pages.length + dangling.length <= SMALL_GRAPH ? { name: 'circle', padding: 60 } : { name: 'cose', padding: 40 },
          // a handful of pages would otherwise be zoomed until labels and arrows swallow the canvas
          maxZoom: 1.5,
          style: [
            { selector: 'node', style: { label: 'data(label)', 'font-size': 12, 'background-color': '#4f6ef7' } },
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
