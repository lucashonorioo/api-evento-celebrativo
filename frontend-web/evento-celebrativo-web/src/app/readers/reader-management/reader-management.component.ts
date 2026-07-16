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
import { ReaderRequest, ReaderResponse } from '../reader.models';
import { ReaderService } from '../reader.service';

const READER_LABELS: PersonManagementLabels = {
  singular: 'leitor',
  singularCapitalized: 'Leitor',
  singularWithArticle: 'O leitor',
  pluralWithArticle: 'os leitores',
  demonstrativeSingular: 'este leitor',
};

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
    phoneNumber: ['', personPhoneNumberValidators()],
    birthdayDate: ['', [Validators.required, pastDateValidator]],
    password: ['', personPasswordValidators()],
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
          this.errorMessage.set(loadingErrorMessageFor(READER_LABELS));
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
          this.successMessage.set(createdSuccessMessageFor(READER_LABELS));
        } else {
          this.readers.update((readers) =>
            readers.map((currentReader) => (currentReader.id === reader.id ? reader : currentReader)),
          );
          this.successMessage.set(updatedSuccessMessageFor(READER_LABELS));
        }

        this.resetForm();
      },
      error: (error: unknown) => {
        this.errorMessage.set(saveErrorMessageFor(error, READER_LABELS));

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
          this.successMessage.set(deletedSuccessMessageFor(READER_LABELS));
        },
        error: (error: unknown) => {
          this.errorMessage.set(deleteErrorMessageFor(error, READER_LABELS));

          if (error instanceof HttpErrorResponse && error.status === 404) {
            this.pendingDeletion.set(null);
            this.loadReaders(false);
          }
        },
      });
  }

  fieldErrorMessage(controlName: keyof ReaderRequest): string | null {
    return fieldErrorMessageFor(this.form.controls[controlName], controlName, READER_LABELS);
  }

  private readerRequest(): ReaderRequest {
    return normalizePersonManagementRequest(this.form.getRawValue());
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
