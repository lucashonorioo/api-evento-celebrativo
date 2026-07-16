import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { normalizePersonManagementRequest } from '../../people/person-form.helpers';
import {
  notBlankValidator,
  pastDateValidator,
  personPasswordValidators,
  personPhoneNumberValidators,
} from '../../people/person-form.validators';
import {
  PersonManagementLabels,
  createdSuccessMessageFor,
  deleteErrorMessageFor,
  deletedSuccessMessageFor,
  fieldErrorMessageFor,
  loadingErrorMessageFor,
  saveErrorMessageFor,
  updatedSuccessMessageFor,
} from '../../people/person-management-messages';
import {
  EucharisticMinisterRequest,
  EucharisticMinisterResponse,
} from '../eucharistic-minister.models';
import { EucharisticMinisterService } from '../eucharistic-minister.service';

const EUCHARISTIC_MINISTER_LABELS: PersonManagementLabels = {
  singular: 'ministro da Eucaristia',
  singularCapitalized: 'Ministro da Eucaristia',
  singularWithArticle: 'O ministro da Eucaristia',
  pluralWithArticle: 'os ministros da Eucaristia',
  demonstrativeSingular: 'este ministro da Eucaristia',
};

@Component({
  selector: 'app-eucharistic-minister-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './eucharistic-minister-management.component.html',
  styleUrl: './eucharistic-minister-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EucharisticMinisterManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly eucharisticMinisterService = inject(EucharisticMinisterService);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', personPasswordValidators()],
  });
  readonly ministers = signal<EucharisticMinisterResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingMinisterId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<EucharisticMinisterResponse | null>(null);

  get isEditing(): boolean {
    return this.editingMinisterId() !== null;
  }

  ngOnInit(): void {
    this.loadMinisters();
  }

  loadMinisters(clearErrorMessage = true): void {
    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.eucharisticMinisterService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (ministers) => {
          this.ministers.set(ministers);
        },
        error: () => {
          this.errorMessage.set(loadingErrorMessageFor(EUCHARISTIC_MINISTER_LABELS));
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

    const request = this.ministerRequest();
    const editingMinisterId = this.editingMinisterId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingMinisterId === null
        ? this.eucharisticMinisterService.create(request)
        : this.eucharisticMinisterService.update(editingMinisterId, request);

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (minister) => {
        if (editingMinisterId === null) {
          this.ministers.update((ministers) => [...ministers, minister]);
          this.successMessage.set(createdSuccessMessageFor(EUCHARISTIC_MINISTER_LABELS));
        } else {
          this.ministers.update((ministers) =>
            ministers.map((currentMinister) =>
              currentMinister.id === minister.id ? minister : currentMinister,
            ),
          );
          this.successMessage.set(updatedSuccessMessageFor(EUCHARISTIC_MINISTER_LABELS));
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error, EUCHARISTIC_MINISTER_LABELS));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadMinisters(false);
        }
      },
    });
  }

  startEditing(minister: EucharisticMinisterResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingMinisterId.set(minister.id);
    this.form.setValue({
      name: minister.name,
      phoneNumber: minister.phoneNumber ?? '',
      birthdayDate: minister.birthdayDate ?? '',
      password: '',
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  cancelEditing(): void {
    this.clearMessages();
    this.resetForm();
  }

  requestDeletion(minister: EucharisticMinisterResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(minister);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const minister = this.pendingDeletion();

    if (minister === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(minister.id);
    this.clearMessages();

    this.eucharisticMinisterService
      .delete(minister.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.ministers.update((ministers) =>
            ministers.filter((currentMinister) => currentMinister.id !== minister.id),
          );

          if (this.editingMinisterId() === minister.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set(deletedSuccessMessageFor(EUCHARISTIC_MINISTER_LABELS));
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error, EUCHARISTIC_MINISTER_LABELS));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadMinisters(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof EucharisticMinisterRequest): string | null {
    return fieldErrorMessageFor(
      this.form.controls[controlName],
      controlName,
      EUCHARISTIC_MINISTER_LABELS,
    );
  }

  private ministerRequest(): EucharisticMinisterRequest {
    return normalizePersonManagementRequest(this.form.getRawValue());
  }

  private resetForm(): void {
    this.editingMinisterId.set(null);
    this.form.reset({
      name: '',
      phoneNumber: '',
      birthdayDate: '',
      password: '',
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
