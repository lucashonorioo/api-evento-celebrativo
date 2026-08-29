import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { MinistryType, UserRole } from '../admin-users/admin-user.models';
import { todayLocalDate } from '../people/person-form.helpers';
import { notBlankValidator, pastDateValidator } from '../people/person-form.validators';
import { CurrentUserProfile, CurrentUserProfileUpdateRequest } from './current-user-profile.models';
import { CurrentUserProfileService } from './current-user-profile.service';

const ROLE_LABELS: Record<UserRole, string> = {
  ROLE_ADMIN: 'Administrador',
  ROLE_OPERATOR: 'Operador',
};

const MINISTRY_LABELS: Record<MinistryType, string> = {
  PRIEST: 'Padre',
  READER: 'Leitor',
  COMMENTATOR: 'Comentarista',
  MINISTER_OF_THE_WORD: 'Ministro da Palavra',
  EUCHARISTIC_MINISTER: 'Ministro da Eucaristia',
};

@Component({
  selector: 'app-current-user-profile',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './current-user-profile.component.html',
  styleUrl: './current-user-profile.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrentUserProfileComponent implements OnInit {
  private readonly currentUserProfileService = inject(CurrentUserProfileService);
  private readonly destroyRef = inject(DestroyRef);

  private hasPopulatedForm = false;

  readonly today = todayLocalDate();
  readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlankValidator],
    }),
    birthdayDate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, pastDateValidator],
    }),
  });

  readonly profile = this.currentUserProfileService.profile;
  readonly isLoading = this.currentUserProfileService.isLoading;
  readonly loadErrorMessage = this.currentUserProfileService.errorMessage;

  readonly isSaving = signal(false);
  readonly saveErrorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  private readonly profile$ = toObservable(this.profile);

  ngOnInit(): void {
    this.profile$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((profile) => {
      if (profile !== null && !this.hasPopulatedForm) {
        this.hasPopulatedForm = true;
        this.applyProfileToForm(profile);
      }
    });
  }

  retryLoad(): void {
    this.currentUserProfileService.loadProfile();
  }

  submit(): void {
    if (this.isSaving()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    this.successMessage.set(null);
    this.saveErrorMessage.set(null);
    this.isSaving.set(true);

    const request: CurrentUserProfileUpdateRequest = {
      name: this.form.controls.name.value.trim(),
      birthdayDate: this.form.controls.birthdayDate.value,
    };

    this.currentUserProfileService.updateProfile(request).subscribe({
      next: (profile) => {
        this.isSaving.set(false);
        this.applyProfileToForm(profile);
        this.successMessage.set('Perfil atualizado com sucesso.');
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.saveErrorMessage.set(saveErrorMessageFor(error));
      },
    });
  }

  fieldErrorMessage(controlName: 'name' | 'birthdayDate'): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched || control.valid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('blank')) {
      return controlName === 'name' ? 'Informe o nome.' : 'Informe a data de nascimento.';
    }

    if (control.hasError('pastDate')) {
      return 'A data de nascimento deve ser anterior a hoje.';
    }

    return null;
  }

  roleLabel(role: UserRole): string {
    return ROLE_LABELS[role] ?? role;
  }

  ministryLabel(ministry: MinistryType): string {
    return MINISTRY_LABELS[ministry] ?? ministry;
  }

  private applyProfileToForm(profile: CurrentUserProfile): void {
    this.form.setValue({
      name: profile.name,
      birthdayDate: profile.birthdayDate,
    });
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }
}

function saveErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'Os dados informados são inválidos. Revise e tente novamente.';
    }

    if (error.status === 403) {
      return 'Não foi possível atualizar o seu perfil.';
    }

    if (error.status === 404) {
      return 'Não foi possível localizar o perfil associado à sua conta autenticada.';
    }
  }

  return 'Não foi possível atualizar o seu perfil. Tente novamente.';
}
