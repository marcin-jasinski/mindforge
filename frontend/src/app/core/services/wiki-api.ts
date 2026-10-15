import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { DocumentView, Graph, Health, IndexEntry, IndexView, KnowledgeBase, Page, Revision } from '../models/api';
import { ApiService } from './api.service';

/** Owns the endpoint paths of a knowledge base, its documents and its wiki; components never see a path. */
@Injectable({ providedIn: 'root' })
export class WikiApi {
  private readonly api = inject(ApiService);

  knowledgeBases(): Observable<KnowledgeBase[]> {
    return this.api.get('/knowledge-bases');
  }

  createKnowledgeBase(name: string, description: string): Observable<KnowledgeBase> {
    return this.api.post('/knowledge-bases', { name, description });
  }

  knowledgeBase(kbId: string): Observable<KnowledgeBase> {
    return this.api.get(`/knowledge-bases/${kbId}`);
  }

  /** A plain link target: the browser downloads it, HttpClient is not involved. */
  exportUrl(kbId: string): string {
    return this.api.url(`/knowledge-bases/${kbId}/export`);
  }

  documents(kbId: string): Observable<DocumentView[]> {
    return this.api.get(`/knowledge-bases/${kbId}/documents`);
  }

  removeDocument(kbId: string, documentId: string): Observable<unknown> {
    return this.api.delete(`/knowledge-bases/${kbId}/documents/${documentId}`);
  }

  uploadDocument(kbId: string, form: FormData): Observable<unknown> {
    return this.api.post(`/knowledge-bases/${kbId}/documents`, form, true);
  }

  index(kbId: string): Observable<IndexView> {
    return this.api.get(`/knowledge-bases/${kbId}/index`);
  }

  search(kbId: string, query: string): Observable<IndexEntry[]> {
    return this.api.get(`/knowledge-bases/${kbId}/pages/search?q=${encodeURIComponent(query)}`);
  }

  page(kbId: string, path: string): Observable<Page> {
    return this.api.get(`/knowledge-bases/${kbId}/pages/${path}`);
  }

  revisions(kbId: string, path: string): Observable<Revision[]> {
    return this.api.get(`/knowledge-bases/${kbId}/pages/${path}/revisions`);
  }

  graph(kbId: string): Observable<Graph> {
    return this.api.get(`/knowledge-bases/${kbId}/graph`);
  }

  health(kbId: string): Observable<Health> {
    return this.api.get(`/knowledge-bases/${kbId}/health`);
  }
}
