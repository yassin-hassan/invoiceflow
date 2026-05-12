import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { QuoteStatus } from './quote.model';

const COLORS: Record<QuoteStatus, { bg: string; fg: string; label: string }> = {
  DRAFT:     { bg: '#e0e0e0', fg: '#424242', label: 'Draft' },
  SENT:      { bg: '#bbdefb', fg: '#0d47a1', label: 'Sent' },
  ACCEPTED:  { bg: '#c8e6c9', fg: '#1b5e20', label: 'Accepted' },
  REJECTED:  { bg: '#ffcdd2', fg: '#b71c1c', label: 'Rejected' },
  CONVERTED: { bg: '#e1bee7', fg: '#4a148c', label: 'Converted' }
};

@Component({
  selector: 'app-status-chip',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      [style.background]="style.bg"
      [style.color]="style.fg"
      style="display:inline-block; padding:2px 10px; border-radius:12px; font-size:0.75rem; font-weight:500; text-transform:uppercase;">
      {{ style.label }}
    </span>
  `
})
export class StatusChipComponent {
  @Input({ required: true }) status!: QuoteStatus;

  get style() {
    return COLORS[this.status];
  }
}
