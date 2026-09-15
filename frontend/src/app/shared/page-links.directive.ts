import { Directive, HostListener, inject } from '@angular/core';
import { Router } from '@angular/router';

/** Opens a rendered page link inside the SPA instead of reloading it. */
@Directive({ selector: '[appPageLinks]', standalone: true })
export class PageLinksDirective {
  private readonly router = inject(Router);

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    const link = (event.target as HTMLElement).closest('a[data-page-link]');
    if (link) {
      event.preventDefault();
      this.router.navigateByUrl(link.getAttribute('href') ?? '/');
    }
  }
}
