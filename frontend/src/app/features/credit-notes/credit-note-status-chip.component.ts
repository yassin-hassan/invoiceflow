import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { CreditNoteStatus } from './credit-note.model';

const COLORS: Record<CreditNoteStatus, { bg: string; fg: string }> = {
  DRAFT:  { bg: '#e0e0e0', fg: '#424242' },
  ISSUED: { bg: '#bbdefb', fg: '#0d47a1' }
};

@Component({
  selector: 'app-credit-note-status-chip',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  template: `
    <span
      [style.background]="style.bg"
      [style.color]="style.fg"
      style="display:inline-block; padding:2px 10px; border-radius:12px; font-size:0.75rem; font-weight:500; text-transform:uppercase;">
      {{ ('creditNotes.status.' + status) | translate }}
    </span>
  `
})
export class CreditNoteStatusChipComponent {
  @Input({ required: true }) status!: CreditNoteStatus;

  get style() {
    return COLORS[this.status];
  }
}
