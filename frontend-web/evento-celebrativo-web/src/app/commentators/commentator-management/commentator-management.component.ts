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
  deleteErrorMessageFor,
  deletedSuccessMessageFor,
  createdSuccessMessageFor,
  fieldErrorMessageFor,
  loadingErrorMessageFor,
  saveErrorMessageFor,
  updatedSuccessMessageFor,
} from '../../people/person-management-messages';
import { CommentatorRequest, CommentatorResponse } from '../commentator.models';
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

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', personPasswordValidators()],
  });
  readonly commentators = signal<CommentatorResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingCommentatorId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<CommentatorResponse | null>(null);

  get isEditing(): boolean {
    return this.editingCommentatorId() !== null;
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

    const request = this.commentatorRequest();
    const editingCommentatorId = this.editingCommentatorId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingCommentatorId === null
        ? this.commentatorService.create(request)
        : this.commentatorService.update(editingCommentatorId, request);

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
      password: '',
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

  fieldErrorMessage(controlName: keyof CommentatorRequest): string | null {
    return fieldErrorMessageFor(this.form.controls[controlName], controlName, COMMENTATOR_LABELS);
  }

  private commentatorRequest(): CommentatorRequest {
    return normalizePersonManagementRequest(this.form.getRawValue());
  }

  private resetForm(): void {
    this.editingCommentatorId.set(null);
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
