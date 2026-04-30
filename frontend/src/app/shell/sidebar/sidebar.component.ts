import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
  input,
  output,
  effect,
  DestroyRef,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  LucideAngularModule,
  LUCIDE_ICONS,
  LucideIconProvider,
  ChevronsLeft,
  ChevronsRight,
  LayoutDashboard,
  Library,
} from 'lucide-angular';

import { ApiService } from '../../core/services/api.service';
import type { UserStatsResponse } from '../../core/models/api.models';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    LucideAngularModule,
  ],
  providers: [
    { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider({ ChevronsLeft, ChevronsRight, LayoutDashboard, Library }) },
  ],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly apiService = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  isMobile = input(false);
  toggle = output<void>();

  sidebarCollapsed = signal(localStorage.getItem('mf-sidebar-collapsed') === 'true');
  stats = signal<UserStatsResponse | null>(null);
  statsLoading = signal(true);

  readonly navItems = [
    { label: 'Pulpit', icon: 'layout-dashboard', route: '/' },
    { label: 'Bazy wiedzy', icon: 'library', route: '/knowledge-bases' },
  ];

  constructor() {
    effect(() => {
      localStorage.setItem('mf-sidebar-collapsed', String(this.sidebarCollapsed()));
    });
  }

  ngOnInit(): void {
    this.apiService
      .getMyStats()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: s => { this.stats.set(s); this.statsLoading.set(false); },
        error: () => this.statsLoading.set(false),
      });
  }

  collapseToggle(): void {
    this.sidebarCollapsed.update(v => !v);
    this.toggle.emit();
  }

  isActive(route: string): boolean {
    return this.router.url === route || (route !== '/' && this.router.url.startsWith(route));
  }
}
