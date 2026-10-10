import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { Login } from './login';

describe('Login', () => {
  function setup(data: Record<string, unknown>, query: Record<string, string>, providers: string[]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { providers: () => of(providers) } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data, queryParamMap: { has: (name: string) => name in query, get: (name: string) => query[name] ?? null } } },
        },
      ],
    });
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('is the register page on the register route and the sign-in page otherwise', () => {
    expect(setup({ registering: true }, {}, []).registering()).toBe(true);
    TestBed.resetTestingModule();
    expect(setup({}, {}, []).registering()).toBe(false);
  });

  it('says so when an identity provider sent the person back with an error', () => {
    const login = setup({}, { error: '' }, []);

    expect(login.error()).toContain('nie powiodło się');
  });

  it('explains a provider that shared no verified email', () => {
    expect(setup({}, { error: 'email' }, []).error()).toContain('zweryfikowanego adresu e-mail');
  });

  it('offers only the providers the server has configured', () => {
    expect(setup({}, {}, ['github']).providers()).toEqual(['github']);
  });
});
