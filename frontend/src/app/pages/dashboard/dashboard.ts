import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';

import { KnowledgeBase } from '../../core/models/api';
import { WikiApi } from '../../core/services/wiki-api';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule, RouterLink, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly wiki = inject(WikiApi);

  readonly knowledgeBases = signal<KnowledgeBase[]>([]);
  readonly name = signal('');
  readonly description = signal('');

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    this.wiki.createKnowledgeBase(this.name(), this.description()).subscribe(() => {
      this.name.set('');
      this.description.set('');
      this.load();
    });
  }

  private load(): void {
    this.wiki.knowledgeBases().subscribe((bases) => this.knowledgeBases.set(bases));
  }
}
