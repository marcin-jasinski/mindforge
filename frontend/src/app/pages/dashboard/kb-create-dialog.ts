import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { KnowledgeBaseService } from '../../core/services/knowledge-base.service';

@Component({
  selector: 'app-kb-create-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatSelectModule,
  ],
  template: `
    <h2 mat-dialog-title>New Knowledge Base</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="200" />
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3" maxlength="2000"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Prompt Language</mat-label>
          <mat-select formControlName="prompt_locale">
            <mat-option value="pl">Polish</mat-option>
            <mat-option value="en">English</mat-option>
          </mat-select>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button (click)="create()" [disabled]="form.invalid">Create</button>
    </mat-dialog-actions>
  `,
  styles: [`.dialog-form { display: flex; flex-direction: column; gap: 4px; min-width: 360px; padding-top: 8px; } mat-form-field { width: 100%; }`],
})
export class KbCreateDialogComponent {
  private readonly kbService = inject(KnowledgeBaseService);
  private readonly dialogRef = inject(MatDialogRef<KbCreateDialogComponent>);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    prompt_locale: ['pl'],
  });

  create() {
    if (this.form.invalid) return;
    this.kbService.create(this.form.getRawValue()).subscribe({
      next: kb => this.dialogRef.close(kb),
      error: (err: Error) => this.snack.open(err.message, 'Close', { duration: 4000 }),
    });
  }
}
