import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CreditNoteStatus } from './credit-note.model';

const COLORS: Record<CreditNoteStatus, { bg: string; fg: string; label: string }> = {
  DRAFT:  { bg: '#e0e0e0', fg: '#424242', label: 'Draft' },
  ISSUED: { bg: '#bbdefb', fg: '#0d47a1', label: 'Issued' }
};

@Component({
  selector: 'app-credit-note-status-chip',
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
export class CreditNoteStatusChipComponent {
  @Input({ required: true }) status!: CreditNoteStatus;

  get style() {
    return COLORS[this.status];
  }
}
