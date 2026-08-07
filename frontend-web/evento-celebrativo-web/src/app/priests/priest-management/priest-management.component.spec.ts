import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { PriestCreateRequest, PriestResponse, PriestUpdateRequest } from '../priest.models';
import { PriestService } from '../priest.service';
import { PriestManagementComponent } from './priest-management.component';

describe('PriestManagementComponent', () => {
  let component: PriestManagementComponent;
  let fixture: ComponentFixture<PriestManagementComponent>;
  let priestService: jasmine.SpyObj<PriestService>;

  const priests: PriestResponse[] = [
    {
      id: 98765,
      name: 'Joao Padre',
      phoneNumber: '34999999992',
      birthdayDate: '1961-02-11',
    },
    {
      id: 54321,
      name: 'Pedro Padre',
      phoneNumber: '34999999993',
      birthdayDate: '1959-05-20',
    },
  ];
  const createRequestWithoutAccess: PriestCreateRequest = {
    name: 'Marcos Padre',
    phoneNumber: '34999999994',
    birthdayDate: '1965-03-15',
    createAccess: false,
  };
  const createRequestWithAccess: PriestCreateRequest = {
    name: 'Marcos Padre',
    phoneNumber: '34999999994',
    birthdayDate: '1965-03-15',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: PriestUpdateRequest = {
    name: 'Marcos Padre',
    phoneNumber: '34999999994',
    birthdayDate: '1965-03-15',
  };

  async function setup(response = of(priests)): Promise<void> {
    priestService = jasmine.createSpyObj<PriestService>('PriestService', [
      'findAll',
      'create',
      'update',
      'delete',
    ]);
    priestService.findAll.and.returnValue(response);
    priestService.create.and.returnValue(
      of({
        id: 111,
        name: createRequestWithoutAccess.name,
        phoneNumber: createRequestWithoutAccess.phoneNumber,
        birthdayDate: createRequestWithoutAccess.birthdayDate,
      }),
    );
    priestService.update.and.returnValue(
      of({
        id: 98765,
        name: updateRequest.name,
        phoneNumber: updateRequest.phoneNumber,
        birthdayDate: updateRequest.birthdayDate,
      }),
    );
    priestService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [PriestManagementComponent],
      providers: [{ provide: PriestService, useValue: priestService }],
    }).compileComponents();

    fixture = TestBed.createComponent(PriestManagementComponent);
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

  it('should load priests on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(priestService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title and cadastral form fields', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar padres');
    expect(text).toContain('Cadastrar padre');
    expect(fixture.nativeElement.querySelector('#name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#phoneNumber')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#birthdayDate')).not.toBeNull();
  });

  it('should show loading while priests are pending', async () => {
    const pendingRequest = new Subject<PriestResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando padres...');

    pendingRequest.next(priests);
    pendingRequest.complete();
  });

  it('should render loaded priests', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Joao Padre');
    expect(text).toContain('Pedro Padre');
    expect(text).toContain('34999999992');
    expect(text).toContain('1961-02-11');
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum padre cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    priestService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(priests),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar os padres. Tente novamente.');

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(priestService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Joao Padre');
  });

  describe('creation without access', () => {
    it('should start with createAccess unchecked and access fields hidden', async () => {
      await setup();

      fixture.detectChanges();

      expect(component.form.controls.createAccess.value).toBeFalse();
      expect(component.showAccessFields()).toBeFalse();
      expect(fixture.nativeElement.querySelector('#password')).toBeNull();
      expect(fixture.nativeElement.querySelector('#confirmPassword')).toBeNull();
    });

    it('should not submit invalid forms and should mark cadastral fields as touched', async () => {
      await setup();

      fixture.detectChanges();
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
      expect(component.form.controls.name.touched).toBeTrue();
      expect(component.form.controls.phoneNumber.touched).toBeTrue();
      expect(component.form.controls.birthdayDate.touched).toBeTrue();
      expect(textContent()).toContain('Informe o nome do padre.');
      expect(textContent()).toContain('Informe o telefone.');
      expect(textContent()).toContain('Informe a data de nascimento.');
    });

    it('should reject blank name and invalid phone and birthday', async () => {
      await setup();

      fixture.detectChanges();
      component.form.patchValue({
        name: '   ',
        phoneNumber: '123',
        birthdayDate: '2999-01-01',
      });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe o nome do padre.');
      expect(textContent()).toContain('Informe um telefone com 11 digitos.');
      expect(textContent()).toContain('Informe uma data de nascimento no passado.');
    });

    it('should create priests without access using the expected payload', async () => {
      await setup();

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
      expect(textContent()).toContain('Padre cadastrado com sucesso.');
      expect(component.form.controls.createAccess.value).toBeFalse();
    });

    it('should trim textual values before submitting', async () => {
      await setup();

      fixture.detectChanges();
      component.form.patchValue({
        name: '  Marcos Padre  ',
        phoneNumber: '34999999994',
        birthdayDate: '1965-03-15',
      });
      clickButton('Cadastrar');

      expect(priestService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });

    it('should expose saving state while creating and prevent duplicate saves', async () => {
      const pendingSave = new Subject<PriestResponse>();
      await setup();
      priestService.create.and.returnValue(pendingSave);

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      clickButton('Cadastrar');

      expect(component.isSaving()).toBeTrue();
      expect(priestService.create).toHaveBeenCalledTimes(1);

      pendingSave.next({
        id: 111,
        name: createRequestWithoutAccess.name,
        phoneNumber: createRequestWithoutAccess.phoneNumber,
        birthdayDate: createRequestWithoutAccess.birthdayDate,
      });
      pendingSave.complete();
    });
  });

  describe('creation with access', () => {
    it('should show password and confirm password fields and require them once checked', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();

      expect(component.showAccessFields()).toBeTrue();
      expect(fixture.nativeElement.querySelector('#password')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('#confirmPassword')).not.toBeNull();

      fillCadastralFields(createRequestWithAccess);
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe a senha.');
      expect(textContent()).toContain('Confirme a senha.');
    });

    it('should reject mismatched passwords', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123456', confirmPassword: '654321' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('As senhas informadas nao coincidem.');
    });

    it('should reject a password shorter than the minimum length', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123', confirmPassword: '123' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe uma senha com pelo menos 6 caracteres.');
    });

    it('should reject a password made only of spaces', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '      ', confirmPassword: '      ' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).not.toHaveBeenCalled();
    });

    it('should create priests with access sending ROLE_OPERATOR and never ROLE_ADMIN', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123456', confirmPassword: '123456' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).toHaveBeenCalledOnceWith(createRequestWithAccess);
      expect(textContent()).not.toContain('ROLE_ADMIN');
      expect(fixture.nativeElement.querySelector('select')).toBeNull();
    });

    it('should not expose confirmPassword to the request payload', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123456', confirmPassword: '123456' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      const sentRequest = priestService.create.calls.mostRecent().args[0] as unknown as Record<
        string,
        unknown
      >;
      expect(sentRequest['confirmPassword']).toBeUndefined();
    });
  });

  describe('unchecking access after filling credentials', () => {
    it('should clear password fields, lift validators and submit without access', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      component.form.patchValue({ password: '123456', confirmPassword: '123456' });

      setCheckbox('createAccess', false);
      fixture.detectChanges();

      expect(component.showAccessFields()).toBeFalse();
      expect(component.form.controls.password.value).toBe('');
      expect(component.form.controls.confirmPassword.value).toBe('');
      expect(fixture.nativeElement.querySelector('#password')).toBeNull();

      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(priestService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });
  });

  it('should show friendly create validation and permission errors', async () => {
    await setup();
    priestService.create.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 400 })),
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );

    fixture.detectChanges();
    fillCadastralFields(createRequestWithoutAccess);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Verifique os dados informados e tente novamente.');
    expect(component.form.controls.name.value).toBe(createRequestWithoutAccess.name);

    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');
  });

  it('should show a friendly message when the phone number is already registered', async () => {
    await setup();
    priestService.create.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { errorCode: 'PERSON_PHONE_NUMBER_CONFLICT' },
          }),
      ),
    );

    fixture.detectChanges();
    fillCadastralFields(createRequestWithoutAccess);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Ja existe uma pessoa cadastrada com este telefone.');
  });

  it('should show a friendly message when the access account username already exists', async () => {
    await setup();
    priestService.create.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { errorCode: 'USER_ACCOUNT_USERNAME_CONFLICT' },
          }),
      ),
    );

    fixture.detectChanges();
    setCheckbox('createAccess', true);
    fixture.detectChanges();
    fillCadastralFields(createRequestWithAccess);
    component.form.patchValue({ password: '123456', confirmPassword: '123456' });
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Ja existe uma conta de acesso utilizando este telefone.');
  });

  it('should enter edit mode without exposing access fields and update priests with cadastral data only', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar padre');
    expect(component.editingPriestId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      name: 'Joao Padre',
      phoneNumber: '34999999992',
      birthdayDate: '1961-02-11',
      createAccess: false,
      password: '',
      confirmPassword: '',
    });
    expect(fixture.nativeElement.querySelector('#createAccess')).toBeNull();
    expect(fixture.nativeElement.querySelector('#password')).toBeNull();
    expect(fixture.nativeElement.querySelector('#confirmPassword')).toBeNull();

    component.form.patchValue({
      name: updateRequest.name,
      phoneNumber: updateRequest.phoneNumber,
      birthdayDate: updateRequest.birthdayDate,
    });
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(priestService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
    expect(component.editingPriestId()).toBeNull();
    expect(textContent()).toContain('Padre atualizado com sucesso.');
  });

  it('should not require a password when editing', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    component.form.patchValue({
      name: updateRequest.name,
      phoneNumber: updateRequest.phoneNumber,
      birthdayDate: updateRequest.birthdayDate,
    });
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(priestService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
  });

  it('should show a friendly message when the account fields are rejected on update', async () => {
    await setup();
    priestService.update.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { errorCode: 'ACCOUNT_FIELDS_NOT_ALLOWED_ON_PERSON_UPDATE' },
          }),
      ),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nao foi possivel atualizar os dados da pessoa porque foram enviados campos de acesso indevidos.',
    );
  });

  it('should show a friendly message on concurrent update conflicts without retrying automatically', async () => {
    await setup();
    priestService.update.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { errorCode: 'CONCURRENT_UPDATE_CONFLICT' },
          }),
      ),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain(
      'Os dados foram alterados simultaneamente. Atualize as informacoes e tente novamente.',
    );
    expect(priestService.update).toHaveBeenCalledTimes(1);
  });

  it('should cancel editing without calling the backend and restore the initial creation state', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(priestService.update).not.toHaveBeenCalled();
    expect(component.editingPriestId()).toBeNull();
    expect(textContent()).toContain('Cadastrar padre');
    expect(component.form.controls.createAccess.value).toBeFalse();
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.confirmPassword.value).toBe('');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    priestService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O padre solicitado nao foi encontrado.');
    expect(priestService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este padre?');
    expect(textContent()).toContain('Joao Padre');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(priestService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete a priest after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(priestService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Padre excluido com sucesso.');
    expect(textContent()).not.toContain('Joao Padre');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    priestService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(priestService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors', async () => {
    await setup();
    priestService.delete.and.returnValues(
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
      'Nao e possivel excluir este padre porque ele esta vinculado a eventos.',
    );
    expect(textContent()).toContain('Joao Padre');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O padre solicitado nao foi encontrado.');
    expect(priestService.findAll).toHaveBeenCalledTimes(2);
  });

  it('should not expose identifiers, passwords, JSON, tokens, or unknown data', async () => {
    await setup();

    fixture.detectChanges();
    setCheckbox('createAccess', true);
    fixture.detectChanges();
    component.form.patchValue({ password: '123456', confirmPassword: '123456' });
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

  function fillCadastralFields(value: {
    name: string;
    phoneNumber: string;
    birthdayDate: string;
  }): void {
    component.form.patchValue({
      name: value.name,
      phoneNumber: value.phoneNumber,
      birthdayDate: value.birthdayDate,
    });
  }

  function setCheckbox(id: string, checked: boolean): void {
    const checkbox = (fixture.nativeElement as HTMLElement).querySelector(
      `#${id}`,
    ) as HTMLInputElement;
    checkbox.checked = checked;
    checkbox.dispatchEvent(new Event('change'));
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
