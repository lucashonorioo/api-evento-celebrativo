import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { EucharisticMinisterResponse } from '../eucharistic-minister.models';
import { EucharisticMinisterService } from '../eucharistic-minister.service';

@Component({
  selector: 'app-eucharistic-minister-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './eucharistic-minister-list.component.html',
  styleUrl: './eucharistic-minister-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EucharisticMinisterListComponent implements OnInit {
  private readonly authSessionService = inject(AuthSessionService);
  private readonly eucharisticMinisterService = inject(EucharisticMinisterService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly ministers = signal<EucharisticMinisterResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadMinisters();
  }

  loadMinisters(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.eucharisticMinisterService
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
    return 'Você não possui permissão para consultar os ministros da Eucaristia.';
  }

  return 'Não foi possível carregar os ministros da Eucaristia. Tente novamente.';
}
