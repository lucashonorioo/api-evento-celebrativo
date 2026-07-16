import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import {
  EucharisticMinisterRequest,
  EucharisticMinisterResponse,
} from '../eucharistic-minister.models';
import { EucharisticMinisterService } from '../eucharistic-minister.service';
import { EucharisticMinisterManagementComponent } from './eucharistic-minister-management.component';

describe('EucharisticMinisterManagementComponent', () => {
  let component: EucharisticMinisterManagementComponent;
  let fixture: ComponentFixture<EucharisticMinisterManagementComponent>;
  let eucharisticMinisterService: jasmine.SpyObj<EucharisticMinisterService>;

  const ministers: EucharisticMinisterResponse[] = [
    {
      id: 98765,
      name: 'Maria Ministra da Eucaristia',
      phoneNumber: '34999999995',
      birthdayDate: '1991-02-11',
    },
    {
      id: 54321,
      name: 'Joao Ministro da Eucaristia',
      phoneNumber: '34999999996',
      birthdayDate: '1989-05-20',
    },
  ];
  const request: EucharisticMinisterRequest = {
    name: 'Ana Ministra da Eucaristia',
    phoneNumber: '34999999997',
    birthdayDate: '1995-03-15',
    password: '123456',
  };

  async function setup(response = of(ministers)): Promise<void> {
    eucharisticMinisterService = jasmine.createSpyObj<EucharisticMinisterService>(
      'EucharisticMinisterService',
      ['findAll', 'create', 'update', 'delete'],
    );
    eucharisticMinisterService.findAll.and.returnValue(response);
    eucharisticMinisterService.create.and.returnValue(
      of({
        id: 111,
        name: request.name,
        phoneNumber: request.phoneNumber,
        birthdayDate: request.birthdayDate,
      }),
    );
    eucharisticMinisterService.update.and.returnValue(
      of({
        id: 98765,
        name: request.name,
        phoneNumber: request.phoneNumber,
        birthdayDate: request.birthdayDate,
      }),
    );
    eucharisticMinisterService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [EucharisticMinisterManagementComponent],
      providers: [{ provide: EucharisticMinisterService, useValue: eucharisticMinisterService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EucharisticMinisterManagementComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('should load ministers on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(eucharisticMinisterService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title and form fields', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar ministros da Eucaristia');
    expect(text).toContain('Cadastrar ministro da Eucaristia');
    expect(fixture.nativeElement.querySelector('#name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#phoneNumber')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#birthdayDate')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#password')).not.toBeNull();
  });

  it('should show loading while ministers are pending', async () => {
    const pendingRequest = new Subject<EucharisticMinisterResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando ministros da Eucaristia...');

    pendingRequest.next(ministers);
    pendingRequest.complete();
  });

  it('should render loaded ministers', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Maria Ministra da Eucaristia');
    expect(text).toContain('Joao Ministro da Eucaristia');
    expect(text).toContain('34999999995');
    expect(text).toContain('1991-02-11');
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum ministro da Eucaristia cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    eucharisticMinisterService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(ministers),
    );

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nao foi possivel carregar os ministros da Eucaristia. Tente novamente.',
    );

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Ministra da Eucaristia');
  });

  it('should not submit invalid forms and should mark fields as touched', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eucharisticMinisterService.create).not.toHaveBeenCalled();
    expect(component.form.controls.name.touched).toBeTrue();
    expect(component.form.controls.phoneNumber.touched).toBeTrue();
    expect(component.form.controls.birthdayDate.touched).toBeTrue();
    expect(component.form.controls.password.touched).toBeTrue();
    expect(textContent()).toContain('Informe o nome do ministro da Eucaristia.');
    expect(textContent()).toContain('Informe o telefone.');
    expect(textContent()).toContain('Informe a data de nascimento.');
    expect(textContent()).toContain('Informe a senha.');
  });

  it('should reject blank name and invalid phone, birthday, and password', async () => {
    await setup();

    fixture.detectChanges();
    component.form.setValue({
      name: '   ',
      phoneNumber: '123',
      birthdayDate: '2999-01-01',
      password: '123',
    });
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eucharisticMinisterService.create).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe o nome do ministro da Eucaristia.');
    expect(textContent()).toContain('Informe um telefone com 11 digitos.');
    expect(textContent()).toContain('Informe uma data de nascimento no passado.');
    expect(textContent()).toContain('Informe uma senha com pelo menos 6 caracteres.');
  });

  it('should create ministers with the expected payload', async () => {
    await setup();

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eucharisticMinisterService.create).toHaveBeenCalledOnceWith(request);
    expect(textContent()).toContain('Ministro da Eucaristia cadastrado com sucesso.');
    expect(textContent()).toContain('Ana Ministra da Eucaristia');
    expect(component.form.getRawValue()).toEqual({
      name: '',
      phoneNumber: '',
      birthdayDate: '',
      password: '',
    });
  });

  it('should trim textual values before submitting', async () => {
    await setup();

    fixture.detectChanges();
    component.form.setValue({
      name: '  Ana Ministra da Eucaristia  ',
      phoneNumber: '34999999997',
      birthdayDate: '1995-03-15',
      password: '123456',
    });
    clickButton('Cadastrar');

    expect(eucharisticMinisterService.create).toHaveBeenCalledOnceWith(request);
  });

  it('should expose saving state while creating and prevent duplicate saves', async () => {
    const pendingSave = new Subject<EucharisticMinisterResponse>();
    await setup();
    eucharisticMinisterService.create.and.returnValue(pendingSave);

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    clickButton('Cadastrar');

    expect(component.isSaving()).toBeTrue();
    expect(eucharisticMinisterService.create).toHaveBeenCalledTimes(1);

    pendingSave.next({
      id: 111,
      name: request.name,
      phoneNumber: request.phoneNumber,
      birthdayDate: request.birthdayDate,
    });
    pendingSave.complete();
  });

  it('should show friendly create validation and permission errors', async () => {
    await setup();
    eucharisticMinisterService.create.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 400 })),
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Verifique os dados informados e tente novamente.');

    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');
  });

  it('should enter edit mode without exposing an existing password and update ministers', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar ministro da Eucaristia');
    expect(component.editingMinisterId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      name: 'Maria Ministra da Eucaristia',
      phoneNumber: '34999999995',
      birthdayDate: '1991-02-11',
      password: '',
    });
    expect(textContent()).not.toContain('123456');

    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(eucharisticMinisterService.update).toHaveBeenCalledOnceWith(98765, request);
    expect(component.editingMinisterId()).toBeNull();
    expect(textContent()).toContain('Ministro da Eucaristia atualizado com sucesso.');
    expect(textContent()).toContain('Ana Ministra da Eucaristia');
  });

  it('should require a new password when editing', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    component.form.patchValue({
      name: request.name,
      phoneNumber: request.phoneNumber,
      birthdayDate: request.birthdayDate,
      password: '',
    });
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(eucharisticMinisterService.update).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe a senha.');
  });

  it('should cancel editing without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(eucharisticMinisterService.update).not.toHaveBeenCalled();
    expect(component.editingMinisterId()).toBeNull();
    expect(textContent()).toContain('Cadastrar ministro da Eucaristia');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    eucharisticMinisterService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O ministro da Eucaristia solicitado nao foi encontrado.');
    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este ministro da Eucaristia?');
    expect(textContent()).toContain('Maria Ministra da Eucaristia');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(eucharisticMinisterService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete a minister after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(eucharisticMinisterService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Ministro da Eucaristia excluido com sucesso.');
    expect(textContent()).not.toContain('Maria Ministra da Eucaristia');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    eucharisticMinisterService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(eucharisticMinisterService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors', async () => {
    await setup();
    eucharisticMinisterService.delete.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 409 })),
      throwError(() => new HttpErrorResponse({ status: 403 })),
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nao e possivel excluir este ministro da Eucaristia porque ele esta vinculado a eventos.',
    );
    expect(textContent()).toContain('Maria Ministra da Eucaristia');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O ministro da Eucaristia solicitado nao foi encontrado.');
    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(2);
  });

  it('should not expose identifiers, passwords, JSON, tokens, or unknown data', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).not.toContain('98765');
    expect(text).not.toContain('54321');
    expect(text).not.toContain('123456');
    expect(text).not.toContain('{');
    expect(text).not.toContain('access_token');
    expect(text).not.toContain('Bearer');
    expect(text).not.toContain('undefined');
    expect(text).not.toContain('null');
  });

  function fillForm(value: EucharisticMinisterRequest): void {
    component.form.setValue(value);
  }

  function clickButton(label: string): void {
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((element): element is HTMLButtonElement => element.textContent?.includes(label) ?? false);

    if (button === undefined) {
      fail(`Button "${label}" not found`);
      return;
    }

    button.click();
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
