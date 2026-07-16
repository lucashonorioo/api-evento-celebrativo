import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { MinisterOfTheWordResponse } from '../minister-of-the-word.models';
import { MinisterOfTheWordService } from '../minister-of-the-word.service';

@Component({
  selector: 'app-minister-of-the-word-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './minister-of-the-word-list.component.html',
  styleUrl: './minister-of-the-word-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MinisterOfTheWordListComponent implements OnInit {
  private readonly authSessionService = inject(AuthSessionService);
  private readonly ministerOfTheWordService = inject(MinisterOfTheWordService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly ministers = signal<MinisterOfTheWordResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMinisters();
  }

  loadMinisters(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.ministerOfTheWordService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (ministers) => {
          this.ministers.set(ministers);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar os ministros da Palavra.';
  }

  return 'Não foi possível carregar os ministros da Palavra. Tente novamente.';
}
