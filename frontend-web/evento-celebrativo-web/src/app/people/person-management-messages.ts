import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl } from '@angular/forms';

export type PersonManagementControlName = 'name' | 'phoneNumber' | 'birthdayDate' | 'password';

export interface PersonManagementLabels {
  singular: string;
  singularCapitalized: string;
  singularWithArticle: string;
  pluralWithArticle: string;
  demonstrativeSingular: string;
}

export function loadingErrorMessageFor(labels: PersonManagementLabels): string {
  return `Nao foi possivel carregar ${labels.pluralWithArticle}. Tente novamente.`;
}

export function createdSuccessMessageFor(labels: PersonManagementLabels): string {
  return `${labels.singularCapitalized} cadastrado com sucesso.`;
}

export function updatedSuccessMessageFor(labels: PersonManagementLabels): string {
  return `${labels.singularCapitalized} atualizado com sucesso.`;
}

export function deletedSuccessMessageFor(labels: PersonManagementLabels): string {
  return `${labels.singularCapitalized} excluido com sucesso.`;
}

export function fieldErrorMessageFor(
  control: AbstractControl,
  controlName: PersonManagementControlName,
  labels: PersonManagementLabels,
): string | null {
  if (!control.touched || control.valid) {
    return null;
  }

  if (control.hasError('required') || control.hasError('blank')) {
    return requiredFieldMessageFor(controlName, labels);
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

export function saveErrorMessageFor(
  error: unknown,
  labels: PersonManagementLabels,
): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'Verifique os dados informados e tente novamente.';
    }

    if (error.status === 403) {
      return 'Voce nao possui permissao para realizar esta operacao.';
    }

    if (error.status === 404) {
      return `${labels.singularWithArticle} solicitado nao foi encontrado.`;
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}

export function deleteErrorMessageFor(
  error: unknown,
  labels: PersonManagementLabels,
): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Voce nao possui permissao para realizar esta operacao.';
    }

    if (error.status === 404) {
      return `${labels.singularWithArticle} solicitado nao foi encontrado.`;
    }

    if (error.status === 409) {
      return `Nao e possivel excluir ${labels.demonstrativeSingular} porque ele esta vinculado a eventos.`;
    }
  }

  return 'Nao foi possivel concluir a operacao. Tente novamente.';
}

function requiredFieldMessageFor(
  controlName: PersonManagementControlName,
  labels: PersonManagementLabels,
): string {
  if (controlName === 'name') {
    return `Informe o nome do ${labels.singular}.`;
  }

  if (controlName === 'phoneNumber') {
    return 'Informe o telefone.';
  }

  if (controlName === 'birthdayDate') {
    return 'Informe a data de nascimento.';
  }

  return 'Informe a senha.';
}
