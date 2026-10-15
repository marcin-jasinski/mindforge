import { Pipe, PipeTransform } from '@angular/core';

/** An ISO instant as the reader's local date and time, e.g. "Oct 10, 2026, 8:56 PM". */
@Pipe({ name: 'dateTime', standalone: true })
export class DateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    const date = value ? new Date(value) : null;
    return date && !Number.isNaN(date.getTime())
      ? date.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' })
      : '';
  }
}
