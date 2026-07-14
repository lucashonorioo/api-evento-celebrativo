import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { ReaderResponse } from '../reader.models';
import { ReaderService } from '../reader.service';

@Component({
  selector: 'app-reader-list',
  standalone: true,
  imports: [],
  templateUrl: './reader-list.component.html',
  styleUrl: './reader-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReaderListComponent implements OnInit {
  private readonly readerService = inject(ReaderService);

  readonly readers = signal<ReaderResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadReaders();
  }

  loadReaders(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.readerService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (readers) => {
          this.readers.set(readers);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar os leitores.';
  }

  return 'Não foi possível carregar os leitores. Tente novamente.';
}
