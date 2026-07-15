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

import { ReaderRequest, ReaderResponse } from '../reader.models';
import { ReaderService } from '../reader.service';

@Component({
  selector: 'app-reader-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './reader-management.component.html',
  styleUrl: './reader-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReaderManagementComponent implements OnInit {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly readerService = inject(ReaderService);

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, notBlankValidator]],
    phoneNumber: ['', [Validators.required, Validators.minLength(11), Validators.maxLength(11)]],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });
  readonly readers = signal<ReaderResponse[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly editingReaderId = signal<number | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingDeletion = signal<ReaderResponse | null>(null);

  get isEditing(): boolean {
    return this.editingReaderId() !== null;
  }

  ngOnInit(): void {
    this.loadReaders();
  }

  loadReaders(clearErrorMessage = true): void {
    this.isLoading.set(true);

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.readerService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (readers) => {
          this.readers.set(readers);
        },
        error: () => {
          this.errorMessage.set('Nao foi possivel carregar os leitores. Tente novamente.');
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

    const request = this.readerRequest();
    const editingReaderId = this.editingReaderId();

    this.setSaving(true);
    this.clearMessages();

    const operation =
      editingReaderId === null
        ? this.readerService.create(request)
        : this.readerService.update(editingReaderId, request);

    operation.pipe(finalize(() => this.setSaving(false))).subscribe({
      next: (reader) => {
        if (editingReaderId === null) {
          this.readers.update((readers) => [...readers, reader]);
          this.successMessage.set('Leitor cadastrado com sucesso.');
        } else {
          this.readers.update((readers) =>
            readers.map((currentReader) => (currentReader.id === reader.id ? reader : currentReader)),
          );
          this.successMessage.set('Leitor atualizado com sucesso.');
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error));

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.loadReaders(false);
        }
      },
    });
  }

  startEditing(reader: ReaderResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(null);
    this.editingReaderId.set(reader.id);
    this.form.setValue({
      name: reader.name,
      phoneNumber: reader.phoneNumber ?? '',
      birthdayDate: reader.birthdayDate ?? '',
      password: '',
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  cancelEditing(): void {
    this.clearMessages();
    this.resetForm();
  }

  requestDeletion(reader: ReaderResponse): void {
    this.clearMessages();
    this.pendingDeletion.set(reader);
  }

  cancelDeletion(): void {
    this.pendingDeletion.set(null);
  }

  confirmDeletion(): void {
    const reader = this.pendingDeletion();

    if (reader === null || this.deletingId() !== null) {
      return;
    }

    this.deletingId.set(reader.id);
    this.clearMessages();

    this.readerService
      .delete(reader.id)
      .pipe(finalize(() => this.deletingId.set(null)))
      .subscribe({
        next: () => {
          this.readers.update((readers) =>
            readers.filter((currentReader) => currentReader.id !== reader.id),
          );

          if (this.editingReaderId() === reader.id) {
            this.resetForm();
          }

          this.pendingDeletion.set(null);
          this.successMessage.set('Leitor excluido com sucesso.');
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadReaders(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof ReaderRequest): string | null {
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

  private readerRequest(): ReaderRequest {
    const value = this.form.getRawValue();

    return {
      name: value.name.trim(),
      phoneNumber: value.phoneNumber.trim(),
      birthdayDate: value.birthdayDate,
      password: value.password,
    };
  }

  private resetForm(): void {
    this.editingReaderId.set(null);
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

function requiredFieldMessageFor(controlName: keyof ReaderRequest): string {
  if (controlName === 'name') {
    return 'Informe o nome do leitor.';
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
      return 'O leitor solicitado nao foi encontrado.';
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
      return 'O leitor solicitado nao foi encontrado.';
    }

    if (error.status === 409) {
      return 'Nao e possivel excluir este leitor porque ele esta vinculado a eventos.';
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}
