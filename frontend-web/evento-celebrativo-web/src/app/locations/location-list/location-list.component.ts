import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { LocationResponse } from '../location.models';
import { LocationService } from '../location.service';

@Component({
  selector: 'app-location-list',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './location-list.component.html',
  styleUrl: './location-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocationListComponent implements OnInit {
  private readonly authSessionService = inject(AuthSessionService);
  private readonly locationService = inject(LocationService);

  readonly isAdmin = this.authSessionService.hasAuthority('ROLE_ADMIN');
  readonly locations = signal<LocationResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadLocations();
  }

  loadLocations(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.locationService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (locations) => {
          this.locations.set(locations);
        },
        error: (error: unknown) => {
          this.errorMessage.set(errorMessageFor(error));
        },
      });
  }
}

function errorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar os locais.';
  }

  return 'Não foi possível carregar os locais. Tente novamente.';
}
