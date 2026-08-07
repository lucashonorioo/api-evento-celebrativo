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
  deleteErrorMessageFor,
  deletedSuccessMessageFor,
  createdSuccessMessageFor,
  fieldErrorMessageFor,
  loadingErrorMessageFor,
  saveErrorMessageFor,
  updatedSuccessMessageFor,
} from '../../people/person-management-messages';
import { CommentatorCreateRequest, CommentatorResponse, CommentatorUpdateRequest } from '../commentator.models';
import { CommentatorService } from '../commentator.service';

const COMMENTATOR_LABELS: PersonManagementLabels = {
  singular: 'comentarista',
  singularCapitalized: 'Comentarista',
  singularWithArticle: 'O comentarista',
  pluralWithArticle: 'os comentaristas',
  demonstrativeSingular: 'este comentarista',
};

@Component({
  selector: 'app-commentator-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './commentator-management.component.html',
  styleUrl: './commentator-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommentatorManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly commentatorService = inject(CommentatorService);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    createAccess: [false],
    password: [''],
    confirmPassword: [''],
  });
  readonly commentators = signal<CommentatorResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingCommentatorId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<CommentatorResponse | null>(null);
  readonly showAccessFields = signal(false);

  get isEditing(): boolean {
    return this.editingCommentatorId() !== null;
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
    this.loadCommentators();
  }

  loadCommentators(clearErrorMessage = true): void {
    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.commentatorService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (commentators) => {
          this.commentators.set(commentators);
        },
        error: () => {
          this.errorMessage.set(loadingErrorMessageFor(COMMENTATOR_LABELS));
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

    const editingCommentatorId = this.editingCommentatorId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingCommentatorId === null
        ? this.commentatorService.create(this.createRequest())
        : this.commentatorService.update(editingCommentatorId, this.updateRequest());

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (commentator) => {
        if (editingCommentatorId === null) {
          this.commentators.update((commentators) => [...commentators, commentator]);
          this.successMessage.set(createdSuccessMessageFor(COMMENTATOR_LABELS));
        } else {
          this.commentators.update((commentators) =>
            commentators.map((currentCommentator) =>
              currentCommentator.id === commentator.id ? commentator : currentCommentator,
            ),
          );
          this.successMessage.set(updatedSuccessMessageFor(COMMENTATOR_LABELS));
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error, COMMENTATOR_LABELS));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadCommentators(false);
        }
      },
    });
  }

  startEditing(commentator: CommentatorResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingCommentatorId.set(commentator.id);
    this.form.setValue({
      name: commentator.name,
      phoneNumber: commentator.phoneNumber ?? '',
      birthdayDate: commentator.birthdayDate ?? '',
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

  requestDeletion(commentator: CommentatorResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(commentator);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const commentator = this.pendingDeletion();

    if (commentator === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(commentator.id);
    this.clearMessages();

    this.commentatorService
      .delete(commentator.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.commentators.update((commentators) =>
            commentators.filter(
              (currentCommentator) => currentCommentator.id !== commentator.id,
            ),
          );

          if (this.editingCommentatorId() === commentator.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set(deletedSuccessMessageFor(COMMENTATOR_LABELS));
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error, COMMENTATOR_LABELS));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadCommentators(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: PersonManagementControlName): string | null {
    return fieldErrorMessageFor(this.form.controls[controlName], controlName, COMMENTATOR_LABELS);
  }

  private createRequest(): CommentatorCreateRequest {
    return buildPersonMinisterialCreateRequest(this.form.getRawValue());
  }

  private updateRequest(): CommentatorUpdateRequest {
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
    this.editingCommentatorId.set(null);
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
