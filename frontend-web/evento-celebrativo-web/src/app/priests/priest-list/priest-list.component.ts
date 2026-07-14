import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { PriestResponse } from '../priest.models';
import { PriestService } from '../priest.service';

@Component({
  selector: 'app-priest-list',
  standalone: true,
  imports: [],
  templateUrl: './priest-list.component.html',
  styleUrl: './priest-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriestListComponent implements OnInit {
  private readonly priestService = inject(PriestService);

  readonly priests = signal<PriestResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadPriests();
  }

  loadPriests(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.priestService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (priests) => {
          this.priests.set(priests);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar os padres.';
  }

  return 'Não foi possível carregar os padres. Tente novamente.';
}
