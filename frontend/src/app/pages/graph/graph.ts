import { Component, ElementRef, OnDestroy, afterNextRender, inject, input, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import type { Core } from 'cytoscape';

import { WikiApi } from '../../core/services/wiki-api';
import { Icon } from '../../shared/icon';

const SMALL_GRAPH = 12;
const CONCEPT = '#a594ff';
const SOURCE = '#5eead4';
const MUTED = '#a0a5bb';

@Component({
  selector: 'app-graph',
  standalone: true,
  imports: [MatButtonModule, Icon],
  template: `
    <div class="section-head">
      <ul class="legend" aria-label="Legend">
        <li><span class="swatch concept"></span> Concept</li>
        <li><span class="swatch source"></span> Source summary</li>
        <li><span class="swatch dangling"></span> Missing page</li>
      </ul>
      <span class="spacer"></span>
      <span class="muted small">Hover a node to see its links. Click to open the page.</span>
      <button mat-stroked-button type="button" (click)="fit()"><app-icon name="refresh" /> Fit</button>
    </div>
    <div class="canvas-wrap card">
      <div #canvas class="canvas" role="img" aria-label="Graph of the pages and the links between them"></div>
      @if (empty()) {
        <p class="overlay muted">No pages yet — the graph fills in as you upload documents.</p>
      }
    </div>
  `,
  styles: `
    .canvas-wrap { position: relative; padding: 0; overflow: hidden;
      background: radial-gradient(circle, rgb(255 255 255 / 0.06) 1px, transparent 1px) 0 0 / 22px 22px, var(--mf-surface); }
    .canvas { height: min(72vh, 46rem); }
    .overlay { position: absolute; inset: 0; display: grid; place-items: center; }
    .legend { display: flex; flex-wrap: wrap; gap: 1rem; margin: 0; padding: 0; list-style: none; font-size: 0.875rem; }
    .legend li { display: inline-flex; align-items: center; gap: 0.4rem; }
    .swatch { display: inline-block; width: 0.8rem; height: 0.8rem; border-radius: 50%; }
    .swatch.concept { background: ${CONCEPT}; }
    .swatch.source { background: ${SOURCE}; border-radius: 0.2rem; }
    .swatch.dangling { border: 2px dashed ${MUTED}; box-sizing: border-box; }
  `,
})
export class GraphView implements OnDestroy {
  private readonly wiki = inject(WikiApi);
  private readonly router = inject(Router);
  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');
  private cy: Core | null = null;

  readonly kbId = input.required<string>();
  readonly empty = signal(false);

  constructor() {
    afterNextRender(() => {
      this.wiki.graph(this.kbId()).subscribe(async (graph) => {
        this.empty.set(!graph.pages.length);
        const cytoscape = (await import('cytoscape')).default;
        const live = new Set(graph.pages.map((page) => page.path));
        const dangling = [...new Set(graph.links.map((link) => link.targetPath))].filter((path) => !live.has(path));
        const degree = new Map<string, number>();
        for (const link of graph.links) {
          degree.set(link.sourcePath, (degree.get(link.sourcePath) ?? 0) + 1);
          degree.set(link.targetPath, (degree.get(link.targetPath) ?? 0) + 1);
        }
        const size = (path: string) => 18 + Math.min(degree.get(path) ?? 0, 12) * 2.5;
        this.cy = cytoscape({
          container: this.canvas().nativeElement,
          elements: [
            ...graph.pages.map((page) => ({
              data: { id: page.path, label: page.title, kind: page.type === 'Concept' ? 'concept' : 'source', size: size(page.path) },
            })),
            ...dangling.map((path) => ({ data: { id: path, label: path, kind: 'dangling', size: 16 } })),
            ...graph.links.map((link, i) => ({ data: { id: `e${i}`, source: link.sourcePath, target: link.targetPath } })),
          ],
          // a force layout collapses a handful of nodes into a clump; a circle keeps every label readable
          layout: graph.pages.length + dangling.length <= SMALL_GRAPH
            ? { name: 'circle', padding: 70 }
            : { name: 'cose', padding: 50, nodeRepulsion: () => 9000, idealEdgeLength: () => 90 },
          // a handful of pages would otherwise be zoomed until labels and arrows swallow the canvas
          maxZoom: 1.6,
          minZoom: 0.2,
          style: [
            {
              selector: 'node',
              style: {
                label: 'data(label)', width: 'data(size)', height: 'data(size)', 'background-color': CONCEPT,
                'font-family': 'Inter Variable, sans-serif', 'font-size': 13, 'font-weight': 500, color: '#e7e8f0',
                'text-valign': 'bottom', 'text-margin-y': 6, 'text-background-color': '#0a0b12',
                'text-background-opacity': 0.75, 'text-background-padding': '3px', 'text-background-shape': 'roundrectangle',
                'border-width': 2, 'border-color': '#0a0b12', 'transition-property': 'opacity', 'transition-duration': 150,
              },
            },
            { selector: 'node[kind = "source"]', style: { 'background-color': SOURCE, shape: 'round-rectangle' } },
            {
              selector: 'node[kind = "dangling"]',
              style: { 'background-opacity': 0, 'border-width': 2, 'border-style': 'dashed', 'border-color': MUTED, color: MUTED },
            },
            {
              selector: 'edge',
              style: {
                width: 1.2, 'curve-style': 'bezier', 'line-color': 'rgba(165, 148, 255, 0.28)',
                'target-arrow-shape': 'triangle', 'target-arrow-color': 'rgba(165, 148, 255, 0.5)', 'arrow-scale': 0.8,
                'transition-property': 'opacity, line-color', 'transition-duration': 150,
              },
            },
            { selector: '.faded', style: { opacity: 0.12 } },
            { selector: 'edge.lit', style: { 'line-color': CONCEPT, 'target-arrow-color': CONCEPT, width: 2 } },
            { selector: 'node.lit', style: { 'border-color': '#ffffff' } },
          ],
        });
        const cy = this.cy;
        cy.on('mouseover', 'node', (event) => {
          const near = event.target.closedNeighborhood();
          cy.elements().difference(near).addClass('faded');
          near.addClass('lit');
        });
        cy.on('mouseout', 'node', () => cy.elements().removeClass('faded lit'));
        cy.on('tap', 'node', (event) => {
          const path = event.target.id() as string;
          if (live.has(path)) {
            this.router.navigate(['/kb', this.kbId(), 'pages', ...path.split('/')]);
          }
        });
      });
    });
  }

  fit(): void {
    this.cy?.animate({ fit: { eles: this.cy.elements(), padding: 50 } }, { duration: 300 });
  }

  ngOnDestroy(): void {
    this.cy?.destroy();
  }
}
