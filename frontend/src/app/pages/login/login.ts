import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';

import { ApiError } from '../../core/models/api';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly registering = signal(false);
  readonly error = signal<string | null>(null);
  readonly displayName = signal('');
  readonly email = signal('');
  readonly password = signal('');

  submit(): void {
    this.error.set(null);
    const request = this.registering()
      ? this.auth.register(this.displayName(), this.email(), this.password())
      : this.auth.login(this.email(), this.password());
    request.subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (response) => this.error.set((response.error as ApiError | null)?.detail ?? 'Nie udało się zalogować.'),
    });
  }
}
