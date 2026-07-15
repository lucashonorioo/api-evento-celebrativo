import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { finalize } from 'rxjs';

import { LocationRequest, LocationResponse } from '../location.models';
import { LocationService } from '../location.service';

@Component({
  selector: 'app-location-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './location-management.component.html',
  styleUrl: './location-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocationManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly locationService = inject(LocationService);

  readonly form = this.formBuilder.group({
    churchName: ['', [Validators.required, notBlankValidator]],
    address: ['', [Validators.required, notBlankValidator]],
  });
  readonly locations = signal<LocationResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingLocationId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<LocationResponse | null>(null);

  get isEditing(): boolean {
    return this.editingLocationId() !== null;
  }

  ngOnInit(): void {
    this.loadLocations();
  }

  loadLocations(clearErrorMessage = true): void {
    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.locationService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (locations) => {
          this.locations.set(locations);
        },
        error: () => {
          this.errorMessage.set('Nao foi possivel carregar os locais. Tente novamente.');
        },
      });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.isSaving()) {
      return;
    }

    const request = this.locationRequest();
    const editingLocationId = this.editingLocationId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingLocationId === null
        ? this.locationService.create(request)
        : this.locationService.update(editingLocationId, request);

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (location) => {
        if (editingLocationId === null) {
          this.locations.update((locations) => [...locations, location]);
          this.successMessage.set('Local cadastrado com sucesso.');
        } else {
          this.locations.update((locations) =>
            locations.map((currentLocation) =>
              currentLocation.id === location.id ? location : currentLocation,
            ),
          );
          this.successMessage.set('Local atualizado com sucesso.');
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadLocations(false);
        }
      },
    });
  }

  startEditing(location: LocationResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingLocationId.set(location.id);
    this.form.setValue({
      churchName: location.churchName,
      address: location.address,
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  cancelEditing(): void {
    this.clearMessages();
    this.resetForm();
  }

  requestDeletion(location: LocationResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(location);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const location = this.pendingDeletion();

    if (location === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(location.id);
    this.clearMessages();

    this.locationService
      .delete(location.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.locations.update((locations) =>
            locations.filter((currentLocation) => currentLocation.id !== location.id),
          );

          if (this.editingLocationId() === location.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set('Local excluido com sucesso.');
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadLocations(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof LocationRequest): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched || control.valid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('blank')) {
      return controlName === 'churchName' ? 'Informe o nome do local.' : 'Informe o endereco.';
    }

    return null;
  }

  private locationRequest(): LocationRequest {
    const value = this.form.getRawValue();

    return {
      churchName: value.churchName.trim(),
      address: value.address.trim(),
    };
  }

  private resetForm(): void {
    this.editingLocationId.set(null);
    this.form.reset({
      churchName: '',
      address: '',
    });
  }

  private clearMessages(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
  }

  private setSaving(isSaving: boolean): void {
    this.isSaving.set(isSaving);

    if (isSaving) {
      this.form.disable({ emitEvent: false });
      return;
    }

    this.form.enable({ emitEvent: false });
  }
}

function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { blank: true }
    : null;
}

function saveErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'Verifique os dados informados e tente novamente.';
    }

    if (error.status === 403) {
      return 'Voce nao possui permissao para realizar esta operacao.';
    }

    if (error.status === 404) {
      return 'O local solicitado nao foi encontrado.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}

function deleteErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Voce nao possui permissao para realizar esta operacao.';
    }

    if (error.status === 404) {
      return 'O local solicitado nao foi encontrado.';
    }

    if (error.status === 409) {
      return 'Nao e possivel excluir este local porque ele esta vinculado a outros registros.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}
