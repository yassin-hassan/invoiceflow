import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InvoiceStatus } from './invoice.model';

const COLORS: Record<InvoiceStatus, { bg: string; fg: string; label: string }> = {
  DRAFT:          { bg: '#e0e0e0', fg: '#424242', label: 'Draft' },
  SENT:           { bg: '#bbdefb', fg: '#0d47a1', label: 'Sent' },
  PARTIALLY_PAID: { bg: '#ffe082', fg: '#5d4037', label: 'Partially paid' },
  PAID:           { bg: '#c8e6c9', fg: '#1b5e20', label: 'Paid' },
  OVERDUE:        { bg: '#ffcdd2', fg: '#b71c1c', label: 'Overdue' },
  CANCELLED:      { bg: '#616161', fg: '#ffffff', label: 'Cancelled' }
};

@Component({
  selector: 'app-invoice-status-chip',
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
export class InvoiceStatusChipComponent {
  @Input({ required: true }) status!: InvoiceStatus;

  get style() {
    return COLORS[this.status];
  }
}
