import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { PageLinksDirective } from './page-links.directive';

@Component({
  standalone: true,
  imports: [PageLinksDirective],
  template: `<div appPageLinks><a id="in" href="/kb/k/pages/concepts/foo">in</a><a id="out" href="https://x.test">out</a></div>`,
})
class Host {}

describe('PageLinksDirective', () => {
  function setup() {
    const router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: Router, useValue: router }] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return { router, el: fixture.nativeElement as HTMLElement };
  }

  it('navigates inside the SPA on a plain click of a page link', () => {
    const { router, el } = setup();
    el.querySelector<HTMLElement>('#in')!.click();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/kb/k/pages/concepts/foo');
  });

  it('leaves external links and modified clicks to the browser', () => {
    const { router, el } = setup();
    el.querySelector<HTMLElement>('#out')!.click();
    el.querySelector('#in')!.dispatchEvent(new MouseEvent('click', { bubbles: true, ctrlKey: true }));
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
