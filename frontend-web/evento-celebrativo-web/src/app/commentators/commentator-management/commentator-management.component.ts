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

import { CommentatorRequest, CommentatorResponse } from '../commentator.models';
import { CommentatorService } from '../commentator.service';

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
    phoneNumber: ['', [Validators.required, Validators.minLength(11), Validators.maxLength(11)]],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', [Validators.required, Validators.minLength(6)]],
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
          this.errorMessage.set('Nao foi possivel carregar os comentaristas. Tente novamente.');
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
          this.successMessage.set('Comentarista cadastrado com sucesso.');
        } else {
          this.commentators.update((commentators) =>
            commentators.map((currentCommentator) =>
              currentCommentator.id === commentator.id ? commentator : currentCommentator,
            ),
          );
          this.successMessage.set('Comentarista atualizado com sucesso.');
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error));

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
          this.successMessage.set('Comentarista excluido com sucesso.');
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadCommentators(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof CommentatorRequest): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched || control.valid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('blank')) {
      return requiredFieldMessageFor(controlName);
    }

    if (
      controlName === 'phoneNumber' &&
      (control.hasError('minlength') || control.hasError('maxlength'))
    ) {
      return 'Informe um telefone com 11 digitos.';
    }

    if (controlName === 'birthdayDate' && control.hasError('pastDate')) {
      return 'Informe uma data de nascimento no passado.';
    }

    if (controlName === 'password' && control.hasError('minlength')) {
      return 'Informe uma senha com pelo menos 6 caracteres.';
    }

    return null;
  }

  private commentatorRequest(): CommentatorRequest {
    const value = this.form.getRawValue();

    return {
      name: value.name.trim(),
      phoneNumber: value.phoneNumber.trim(),
      birthdayDate: value.birthdayDate,
      password: value.password,
    };
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

function notBlankValidator(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim().length === 0
    ? { blank: true }
    : null;
}

function pastDateValidator(control: AbstractControl): ValidationErrors | null {
  if (typeof control.value !== 'string' || control.value.length === 0) {
    return null;
  }

  return control.value < todayLocalDate() ? null : { pastDate: true };
}

function todayLocalDate(): string {
  const today = new Date();
  const month = `${today.getMonth() + 1}`.padStart(2, '0');
  const day = `${today.getDate()}`.padStart(2, '0');

  return `${today.getFullYear()}-${month}-${day}`;
}

function requiredFieldMessageFor(controlName: keyof CommentatorRequest): string {
  if (controlName === 'name') {
    return 'Informe o nome do comentarista.';
  }

  if (controlName === 'phoneNumber') {
    return 'Informe o telefone.';
  }

  if (controlName === 'birthdayDate') {
    return 'Informe a data de nascimento.';
  }

  return 'Informe a senha.';
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
      return 'O comentarista solicitado nao foi encontrado.';
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
      return 'O comentarista solicitado nao foi encontrado.';
    }

    if (error.status === 409) {
      return 'Nao e possivel excluir este comentarista porque ele esta vinculado a eventos.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}
