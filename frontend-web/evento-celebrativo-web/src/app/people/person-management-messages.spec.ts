import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, Validators } from '@angular/forms';

import {
  PersonManagementLabels,
  createdSuccessMessageFor,
  deletedSuccessMessageFor,
  deleteErrorMessageFor,
  fieldErrorMessageFor,
  loadingErrorMessageFor,
  saveErrorMessageFor,
  updatedSuccessMessageFor,
} from './person-management-messages';

describe('person management messages', () => {
  const labels: PersonManagementLabels = {
    singular: 'leitor',
    singularCapitalized: 'Leitor',
    singularWithArticle: 'O leitor',
    pluralWithArticle: 'os leitores',
    demonstrativeSingular: 'este leitor',
  };

  it('should build common success and loading messages from resource labels', () => {
    expect(loadingErrorMessageFor(labels)).toBe(
      'Nao foi possivel carregar os leitores. Tente novamente.',
    );
    expect(createdSuccessMessageFor(labels)).toBe('Leitor cadastrado com sucesso.');
    expect(updatedSuccessMessageFor(labels)).toBe('Leitor atualizado com sucesso.');
    expect(deletedSuccessMessageFor(labels)).toBe('Leitor excluido com sucesso.');
  });

  it('should build field validation messages without changing existing text', () => {
    const control = new FormControl('', Validators.required);

    control.markAsTouched();
    control.updateValueAndValidity();

    expect(fieldErrorMessageFor(control, 'name', labels)).toBe('Informe o nome do leitor.');
    expect(fieldErrorMessageFor(control, 'phoneNumber', labels)).toBe('Informe o telefone.');
    expect(fieldErrorMessageFor(control, 'birthdayDate', labels)).toBe(
      'Informe a data de nascimento.',
    );
    expect(fieldErrorMessageFor(control, 'password', labels)).toBe('Informe a senha.');
  });

  it('should build friendly save error messages', () => {
    expect(saveErrorMessageFor(new HttpErrorResponse({ status: 400 }), labels)).toBe(
      'Verifique os dados informados e tente novamente.',
    );
    expect(saveErrorMessageFor(new HttpErrorResponse({ status: 403 }), labels)).toBe(
      'Voce nao possui permissao para realizar esta operacao.',
    );
    expect(saveErrorMessageFor(new HttpErrorResponse({ status: 404 }), labels)).toBe(
      'O leitor solicitado nao foi encontrado.',
    );
    expect(saveErrorMessageFor(new HttpErrorResponse({ status: 500 }), labels)).toBe(
      'Nao foi possivel concluir a operacao. Tente novamente.',
    );
  });

  it('should build friendly delete error messages', () => {
    expect(deleteErrorMessageFor(new HttpErrorResponse({ status: 403 }), labels)).toBe(
      'Voce nao possui permissao para realizar esta operacao.',
    );
    expect(deleteErrorMessageFor(new HttpErrorResponse({ status: 404 }), labels)).toBe(
      'O leitor solicitado nao foi encontrado.',
    );
    expect(deleteErrorMessageFor(new HttpErrorResponse({ status: 409 }), labels)).toBe(
      'Nao e possivel excluir este leitor porque ele esta vinculado a eventos.',
    );
    expect(deleteErrorMessageFor(new HttpErrorResponse({ status: 500 }), labels)).toBe(
      'Nao foi possivel concluir a operacao. Tente novamente.',
    );
  });
});
