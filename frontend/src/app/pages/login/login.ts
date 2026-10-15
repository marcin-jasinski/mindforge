import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiError } from '../../core/models/api';
import { AuthService } from '../../core/services/auth.service';
import { apiErrorMessage } from '../../shared/labels';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
})
export class Login implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** /register and /login are two routes of one page, so each can be linked to and reloaded. */
  readonly registering = signal(this.route.snapshot.data['registering'] === true);
  readonly providers = signal<string[]>([]);
  readonly error = signal<string | null>(null);
  readonly displayName = signal('');
  readonly email = signal('');
  readonly password = signal('');

  ngOnInit(): void {
    // the identity provider's callback returns here with ?error when it could not sign the person in
    const failure = this.route.snapshot.queryParamMap;
    if (failure.has('error')) {
      this.error.set(failure.get('error') === 'email'
        ? 'The provider did not share a verified e-mail address, so you cannot sign in. Use your e-mail and password.'
        : 'Sign-in with the external provider failed. Try again or use your e-mail.');
    }
    this.auth.providers().subscribe((providers) => this.providers.set(providers));
  }

  toggle(): void {
    this.router.navigateByUrl(this.registering() ? '/login' : '/register');
  }

  submit(): void {
    this.error.set(null);
    const request = this.registering()
      ? this.auth.register(this.displayName(), this.email(), this.password())
      : this.auth.login(this.email(), this.password());
    request.subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (response) => this.error.set(apiErrorMessage(response.error as ApiError | null, 'Sign-in failed.')),
    });
  }
}
