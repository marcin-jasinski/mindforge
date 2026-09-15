import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, RouterLink, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly api = inject(ApiService);

  readonly knowledgeBases = signal<KnowledgeBase[]>([]);
  readonly name = signal('');
  readonly description = signal('');

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    this.api.post<KnowledgeBase>('/knowledge-bases', { name: this.name(), description: this.description() }).subscribe(() => {
      this.name.set('');
      this.description.set('');
      this.load();
    });
  }

  private load(): void {
    this.api.get<KnowledgeBase[]>('/knowledge-bases').subscribe((bases) => this.knowledgeBases.set(bases));
  }
}
