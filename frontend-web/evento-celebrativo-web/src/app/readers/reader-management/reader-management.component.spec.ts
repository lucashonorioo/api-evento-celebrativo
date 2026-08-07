import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { ReaderCreateRequest, ReaderResponse, ReaderUpdateRequest } from '../reader.models';
import { ReaderService } from '../reader.service';
import { ReaderManagementComponent } from './reader-management.component';

describe('ReaderManagementComponent', () => {
  let component: ReaderManagementComponent;
  let fixture: ComponentFixture<ReaderManagementComponent>;
  let readerService: jasmine.SpyObj<ReaderService>;

  const readers: ReaderResponse[] = [
    {
      id: 98765,
      name: 'Maria Leitora',
      phoneNumber: '34999999992',
      birthdayDate: '1991-02-11',
    },
    {
      id: 54321,
      name: 'Joao Leitor',
      phoneNumber: '34999999993',
      birthdayDate: '1989-05-20',
    },
  ];
  const createRequestWithoutAccess: ReaderCreateRequest = {
    name: 'Ana Leitora',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
    createAccess: false,
  };
  const createRequestWithAccess: ReaderCreateRequest = {
    name: 'Ana Leitora',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: ReaderUpdateRequest = {
    name: 'Ana Leitora',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
  };

  async function setup(response = of(readers)): Promise<void> {
    readerService = jasmine.createSpyObj<ReaderService>('ReaderService', [
      'findAll',
      'create',
      'update',
      'delete',
    ]);
    readerService.findAll.and.returnValue(response);
    readerService.create.and.returnValue(
      of({
        id: 111,
        name: createRequestWithoutAccess.name,
        phoneNumber: createRequestWithoutAccess.phoneNumber,
        birthdayDate: createRequestWithoutAccess.birthdayDate,
      }),
    );
    readerService.update.and.returnValue(
      of({
        id: 98765,
        name: updateRequest.name,
        phoneNumber: updateRequest.phoneNumber,
        birthdayDate: updateRequest.birthdayDate,
      }),
    );
    readerService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [ReaderManagementComponent],
      providers: [{ provide: ReaderService, useValue: readerService }],
    }).compileComponents();

    fixture = TestBed.createComponent(ReaderManagementComponent);
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

  it('should load readers on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(readerService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title and cadastral form fields', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar leitores');
    expect(text).toContain('Cadastrar leitor');
    expect(fixture.nativeElement.querySelector('#name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#phoneNumber')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#birthdayDate')).not.toBeNull();
  });

  it('should show loading while readers are pending', async () => {
    const pendingRequest = new Subject<ReaderResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando leitores...');

    pendingRequest.next(readers);
    pendingRequest.complete();
  });

  it('should render loaded readers', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Maria Leitora');
    expect(text).toContain('Joao Leitor');
    expect(text).toContain('34999999992');
    expect(text).toContain('1991-02-11');
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum leitor cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    readerService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(readers),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar os leitores. Tente novamente.');

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(readerService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Leitora');
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

      expect(readerService.create).not.toHaveBeenCalled();
      expect(component.form.controls.name.touched).toBeTrue();
      expect(component.form.controls.phoneNumber.touched).toBeTrue();
      expect(component.form.controls.birthdayDate.touched).toBeTrue();
      expect(textContent()).toContain('Informe o nome do leitor.');
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

      expect(readerService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe o nome do leitor.');
      expect(textContent()).toContain('Informe um telefone com 11 digitos.');
      expect(textContent()).toContain('Informe uma data de nascimento no passado.');
    });

    it('should create readers without access using the expected payload', async () => {
      await setup();

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(readerService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
      expect(textContent()).toContain('Leitor cadastrado com sucesso.');
      expect(component.form.controls.createAccess.value).toBeFalse();
    });

    it('should trim textual values before submitting', async () => {
      await setup();

      fixture.detectChanges();
      component.form.patchValue({
        name: '  Ana Leitora  ',
        phoneNumber: '34999999994',
        birthdayDate: '1995-03-15',
      });
      clickButton('Cadastrar');

      expect(readerService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });

    it('should expose saving state while creating and prevent duplicate saves', async () => {
      const pendingSave = new Subject<ReaderResponse>();
      await setup();
      readerService.create.and.returnValue(pendingSave);

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      clickButton('Cadastrar');

      expect(component.isSaving()).toBeTrue();
      expect(readerService.create).toHaveBeenCalledTimes(1);

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

      expect(readerService.create).not.toHaveBeenCalled();
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

      expect(readerService.create).not.toHaveBeenCalled();
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

      expect(readerService.create).not.toHaveBeenCalled();
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

      expect(readerService.create).not.toHaveBeenCalled();
    });

    it('should create readers with access sending ROLE_OPERATOR and never ROLE_ADMIN', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123456', confirmPassword: '123456' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(readerService.create).toHaveBeenCalledOnceWith(createRequestWithAccess);
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

      const sentRequest = readerService.create.calls.mostRecent().args[0] as unknown as Record<
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

      expect(readerService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });
  });

  it('should show friendly create validation and permission errors', async () => {
    await setup();
    readerService.create.and.returnValues(
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
    readerService.create.and.returnValue(
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
    readerService.create.and.returnValue(
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

  it('should enter edit mode without exposing access fields and update readers with cadastral data only', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar leitor');
    expect(component.editingReaderId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      name: 'Maria Leitora',
      phoneNumber: '34999999992',
      birthdayDate: '1991-02-11',
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

    expect(readerService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
    expect(component.editingReaderId()).toBeNull();
    expect(textContent()).toContain('Leitor atualizado com sucesso.');
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

    expect(readerService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
  });

  it('should show a friendly message when the account fields are rejected on update', async () => {
    await setup();
    readerService.update.and.returnValue(
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
    readerService.update.and.returnValue(
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
    expect(readerService.update).toHaveBeenCalledTimes(1);
  });

  it('should cancel editing without calling the backend and restore the initial creation state', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(readerService.update).not.toHaveBeenCalled();
    expect(component.editingReaderId()).toBeNull();
    expect(textContent()).toContain('Cadastrar leitor');
    expect(component.form.controls.createAccess.value).toBeFalse();
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.confirmPassword.value).toBe('');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    readerService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O leitor solicitado nao foi encontrado.');
    expect(readerService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este leitor?');
    expect(textContent()).toContain('Maria Leitora');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(readerService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete a reader after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(readerService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Leitor excluido com sucesso.');
    expect(textContent()).not.toContain('Maria Leitora');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    readerService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(readerService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors', async () => {
    await setup();
    readerService.delete.and.returnValues(
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
      'Nao e possivel excluir este leitor porque ele esta vinculado a eventos.',
    );
    expect(textContent()).toContain('Maria Leitora');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O leitor solicitado nao foi encontrado.');
    expect(readerService.findAll).toHaveBeenCalledTimes(2);
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
