import { Pipe, PipeTransform } from '@angular/core';

/** An ISO instant as the reader's local date and time, e.g. "10 paź 2026, 20:56". */
@Pipe({ name: 'dateTime', standalone: true })
export class DateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime())
      ? date.toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })
      : '';
  }
}
