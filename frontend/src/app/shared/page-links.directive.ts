import { Directive, HostListener, inject } from '@angular/core';
import { Router } from '@angular/router';

/** Opens a rendered page link inside the SPA instead of reloading it. */
@Directive({ selector: '[appPageLinks]', standalone: true })
export class PageLinksDirective {
  private readonly router = inject(Router);

  @HostListener('click', ['$event'])
  onClick(event: MouseEvent): void {
    // [innerHTML] strips data-* attributes, so a page link is known by its in-app href
    const link = (event.target as HTMLElement).closest('a[href^="/kb/"]');
    if (link && event.button === 0 && !event.ctrlKey && !event.metaKey && !event.shiftKey) {
      event.preventDefault();
      this.router.navigateByUrl(link.getAttribute('href') ?? '/');
    }
  }
}
