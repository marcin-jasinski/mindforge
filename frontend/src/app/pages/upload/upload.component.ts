import { Component, signal } from '@angular/core';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-upload',
  standalone: true,
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.css',
})
export class UploadComponent {
  dragging = signal(false);
  uploading = signal(false);
  message = signal('');
  error = signal('');
  selectedFile = signal<File | null>(null);

  private readonly maxSize = 5 * 1024 * 1024; // 5 MB

  constructor(private api: ApiService) {}

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragging.set(false);

    const files = event.dataTransfer?.files;
    if (files?.length) this.selectFile(files[0]);
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.selectFile(input.files[0]);
  }

  private selectFile(file: File) {
    this.error.set('');
    this.message.set('');

    if (!file.name.endsWith('.md')) {
      this.error.set('Dozwolone są tylko pliki .md (Markdown).');
      return;
    }
    if (file.size > this.maxSize) {
      this.error.set('Plik jest za duży (max 5 MB).');
      return;
    }
    this.selectedFile.set(file);
  }

  upload() {
    const file = this.selectedFile();
    if (!file || this.uploading()) return;

    this.uploading.set(true);
    this.error.set('');
    this.message.set('');

    this.api.uploadLesson(file).subscribe({
      next: res => {
        this.message.set(res.message);
        this.uploading.set(false);
        this.selectedFile.set(null);
      },
      error: err => {
        this.error.set(err.error?.detail || 'Błąd przesyłania.');
        this.uploading.set(false);
      },
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    return (bytes / 1024).toFixed(1) + ' KB';
  }
}
