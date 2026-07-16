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
  MinisterOfTheWordRequest,
  MinisterOfTheWordResponse,
} from '../minister-of-the-word.models';
import { MinisterOfTheWordService } from '../minister-of-the-word.service';

const MINISTER_OF_THE_WORD_LABELS: PersonManagementLabels = {
  singular: 'ministro da Palavra',
  singularCapitalized: 'Ministro da Palavra',
  singularWithArticle: 'O ministro da Palavra',
  pluralWithArticle: 'os ministros da Palavra',
  demonstrativeSingular: 'este ministro da Palavra',
};

@Component({
  selector: 'app-minister-of-the-word-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './minister-of-the-word-management.component.html',
  styleUrl: './minister-of-the-word-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MinisterOfTheWordManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly ministerOfTheWordService = inject(MinisterOfTheWordService);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', personPasswordValidators()],
  });
  readonly ministers = signal<MinisterOfTheWordResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingMinisterId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<MinisterOfTheWordResponse | null>(null);

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

    this.ministerOfTheWordService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (ministers) => {
          this.ministers.set(ministers);
        },
        error: () => {
          this.errorMessage.set(loadingErrorMessageFor(MINISTER_OF_THE_WORD_LABELS));
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
        ? this.ministerOfTheWordService.create(request)
        : this.ministerOfTheWordService.update(editingMinisterId, request);

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (minister) => {
        if (editingMinisterId === null) {
          this.ministers.update((ministers) => [...ministers, minister]);
          this.successMessage.set(createdSuccessMessageFor(MINISTER_OF_THE_WORD_LABELS));
        } else {
          this.ministers.update((ministers) =>
            ministers.map((currentMinister) =>
              currentMinister.id === minister.id ? minister : currentMinister,
            ),
          );
          this.successMessage.set(updatedSuccessMessageFor(MINISTER_OF_THE_WORD_LABELS));
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error, MINISTER_OF_THE_WORD_LABELS));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadMinisters(false);
        }
      },
    });
  }

  startEditing(minister: MinisterOfTheWordResponse): void {
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

  requestDeletion(minister: MinisterOfTheWordResponse): void {
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

    this.ministerOfTheWordService
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
          this.successMessage.set(deletedSuccessMessageFor(MINISTER_OF_THE_WORD_LABELS));
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error, MINISTER_OF_THE_WORD_LABELS));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadMinisters(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof MinisterOfTheWordRequest): string | null {
    return fieldErrorMessageFor(
      this.form.controls[controlName],
      controlName,
      MINISTER_OF_THE_WORD_LABELS,
    );
  }

  private ministerRequest(): MinisterOfTheWordRequest {
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
