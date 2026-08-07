import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  buildPersonMinisterialCreateRequest,
  buildPersonMinisterialUpdateRequest,
} from '../../people/person-form.helpers';
import {
  matchesControlValidator,
  notBlankValidator,
  pastDateValidator,
  personPasswordValidators,
  personPhoneNumberValidators,
} from '../../people/person-form.validators';
import {
  PersonManagementControlName,
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
  MinisterOfTheWordCreateRequest,
  MinisterOfTheWordResponse,
  MinisterOfTheWordUpdateRequest,
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
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    createAccess: [false],
    password: [''],
    confirmPassword: [''],
  });
  readonly ministers = signal<MinisterOfTheWordResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingMinisterId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<MinisterOfTheWordResponse | null>(null);
  readonly showAccessFields = signal(false);

  get isEditing(): boolean {
    return this.editingMinisterId() !== null;
  }

  constructor() {
    this.form.controls.createAccess.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((createAccess) => this.applyAccessValidators(createAccess));

    this.form.controls.password.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.form.controls.createAccess.value) {
          this.form.controls.confirmPassword.updateValueAndValidity({ onlySelf: true });
        }
      });
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

    const editingMinisterId = this.editingMinisterId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingMinisterId === null
        ? this.ministerOfTheWordService.create(this.createRequest())
        : this.ministerOfTheWordService.update(editingMinisterId, this.updateRequest());

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
      createAccess: false,
      password: '',
      confirmPassword: '',
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

  fieldErrorMessage(controlName: PersonManagementControlName): string | null {
    return fieldErrorMessageFor(
      this.form.controls[controlName],
      controlName,
      MINISTER_OF_THE_WORD_LABELS,
    );
  }

  private createRequest(): MinisterOfTheWordCreateRequest {
    return buildPersonMinisterialCreateRequest(this.form.getRawValue());
  }

  private updateRequest(): MinisterOfTheWordUpdateRequest {
    return buildPersonMinisterialUpdateRequest(this.form.getRawValue());
  }

  private applyAccessValidators(createAccess: boolean): void {
    this.showAccessFields.set(createAccess);

    const passwordControl = this.form.controls.password;
    const confirmPasswordControl = this.form.controls.confirmPassword;

    if (createAccess) {
      passwordControl.setValidators(personPasswordValidators());
      confirmPasswordControl.setValidators([
        Validators.required,
        matchesControlValidator(passwordControl),
      ]);
    } else {
      passwordControl.clearValidators();
      confirmPasswordControl.clearValidators();
      passwordControl.setValue('');
      confirmPasswordControl.setValue('');
    }

    passwordControl.updateValueAndValidity();
    confirmPasswordControl.updateValueAndValidity();
  }

  private resetForm(): void {
    this.editingMinisterId.set(null);
    this.form.reset({
      name: '',
      phoneNumber: '',
      birthdayDate: '',
      createAccess: false,
      password: '',
      confirmPassword: '',
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
