import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

import { CommentatorResponse } from '../commentator.models';
import { CommentatorService } from '../commentator.service';

@Component({
  selector: 'app-commentator-list',
  standalone: true,
  imports: [],
  templateUrl: './commentator-list.component.html',
  styleUrl: './commentator-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommentatorListComponent implements OnInit {
  private readonly commentatorService = inject(CommentatorService);

  readonly commentators = signal<CommentatorResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadCommentators();
  }

  loadCommentators(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.commentatorService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (commentators) => {
          this.commentators.set(commentators);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar os comentaristas.';
  }

  return 'Não foi possível carregar os comentaristas. Tente novamente.';
}
