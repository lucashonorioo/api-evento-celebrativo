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
import { PriestCreateRequest, PriestResponse, PriestUpdateRequest } from '../priest.models';
import { PriestService } from '../priest.service';

const PRIEST_LABELS: PersonManagementLabels = {
  singular: 'padre',
  singularCapitalized: 'Padre',
  singularWithArticle: 'O padre',
  pluralWithArticle: 'os padres',
  demonstrativeSingular: 'este padre',
};

@Component({
  selector: 'app-priest-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './priest-management.component.html',
  styleUrl: './priest-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PriestManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly priestService = inject(PriestService);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    createAccess: [false],
    password: [''],
    confirmPassword: [''],
  });
  readonly priests = signal<PriestResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingPriestId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<PriestResponse | null>(null);
  readonly showAccessFields = signal(false);

  get isEditing(): boolean {
    return this.editingPriestId() !== null;
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
    this.loadPriests();
  }

  loadPriests(clearErrorMessage = true): void {
    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.priestService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (priests) => {
          this.priests.set(priests);
        },
        error: () => {
          this.errorMessage.set(loadingErrorMessageFor(PRIEST_LABELS));
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

    const editingPriestId = this.editingPriestId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingPriestId === null
        ? this.priestService.create(this.createRequest())
        : this.priestService.update(editingPriestId, this.updateRequest());

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (priest) => {
        if (editingPriestId === null) {
          this.priests.update((priests) => [...priests, priest]);
          this.successMessage.set(createdSuccessMessageFor(PRIEST_LABELS));
        } else {
          this.priests.update((priests) =>
            priests.map((currentPriest) =>
              currentPriest.id === priest.id ? priest : currentPriest,
            ),
          );
          this.successMessage.set(updatedSuccessMessageFor(PRIEST_LABELS));
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error, PRIEST_LABELS));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadPriests(false);
        }
      },
    });
  }

  startEditing(priest: PriestResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingPriestId.set(priest.id);
    this.form.setValue({
      name: priest.name,
      phoneNumber: priest.phoneNumber ?? '',
      birthdayDate: priest.birthdayDate ?? '',
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

  requestDeletion(priest: PriestResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(priest);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const priest = this.pendingDeletion();

    if (priest === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(priest.id);
    this.clearMessages();

    this.priestService
      .delete(priest.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.priests.update((priests) =>
            priests.filter((currentPriest) => currentPriest.id !== priest.id),
          );

          if (this.editingPriestId() === priest.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set(deletedSuccessMessageFor(PRIEST_LABELS));
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error, PRIEST_LABELS));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadPriests(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: PersonManagementControlName): string | null {
    return fieldErrorMessageFor(this.form.controls[controlName], controlName, PRIEST_LABELS);
  }

  private createRequest(): PriestCreateRequest {
    return buildPersonMinisterialCreateRequest(this.form.getRawValue());
  }

  private updateRequest(): PriestUpdateRequest {
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
    this.editingPriestId.set(null);
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
