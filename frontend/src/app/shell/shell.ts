import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  inject,
  signal,
} from '@angular/core';
import {
  Router,
  RouterOutlet,
  RouterLink,
  RouterLinkActive,
  NavigationEnd,
} from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs/operators';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatIconModule,
    MatButtonModule, MatListModule, MatMenuModule,
    MatDividerModule, MatTooltipModule,
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class ShellComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly bp = inject(BreakpointObserver);

  readonly isMobile = signal(false);
  readonly sidenavOpen = signal(true);
  readonly currentKbId = signal<string | null>(null);

  constructor() {
    this.bp
      .observe([Breakpoints.XSmall, Breakpoints.Small])
      .pipe(takeUntilDestroyed())
      .subscribe(state => {
        this.isMobile.set(state.matches);
        this.sidenavOpen.set(!state.matches);
      });

    this.router.events
      .pipe(
        filter(e => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(e => {
        const url = (e as NavigationEnd).urlAfterRedirects;
        const match = url.match(/\/kb\/([^/?#]+)/);
        this.currentKbId.set(match ? match[1] : null);
        if (this.isMobile()) {
          this.sidenavOpen.set(false);
        }
      });
  }

  ngOnInit() {
    const match = this.router.url.match(/\/kb\/([^/?#]+)/);
    this.currentKbId.set(match ? match[1] : null);
  }

  toggleSidenav() {
    this.sidenavOpen.update(v => !v);
  }

  logout() {
    this.auth.logout().subscribe();
  }
}
