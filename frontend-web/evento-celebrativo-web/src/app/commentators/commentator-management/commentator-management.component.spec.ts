import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import {
  CommentatorCreateRequest,
  CommentatorResponse,
  CommentatorUpdateRequest,
} from '../commentator.models';
import { CommentatorService } from '../commentator.service';
import { CommentatorManagementComponent } from './commentator-management.component';

describe('CommentatorManagementComponent', () => {
  let component: CommentatorManagementComponent;
  let fixture: ComponentFixture<CommentatorManagementComponent>;
  let commentatorService: jasmine.SpyObj<CommentatorService>;

  const commentators: CommentatorResponse[] = [
    {
      id: 98765,
      name: 'Maria Comentarista',
      phoneNumber: '34999999992',
      birthdayDate: '1991-02-11',
    },
    {
      id: 54321,
      name: 'Joao Comentarista',
      phoneNumber: '34999999993',
      birthdayDate: '1989-05-20',
    },
  ];
  const createRequestWithoutAccess: CommentatorCreateRequest = {
    name: 'Ana Comentarista',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
    createAccess: false,
  };
  const createRequestWithAccess: CommentatorCreateRequest = {
    name: 'Ana Comentarista',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
    createAccess: true,
    password: '123456',
    accessRole: 'ROLE_OPERATOR',
  };
  const updateRequest: CommentatorUpdateRequest = {
    name: 'Ana Comentarista',
    phoneNumber: '34999999994',
    birthdayDate: '1995-03-15',
  };

  async function setup(response = of(commentators)): Promise<void> {
    commentatorService = jasmine.createSpyObj<CommentatorService>('CommentatorService', [
      'findAll',
      'create',
      'update',
      'delete',
    ]);
    commentatorService.findAll.and.returnValue(response);
    commentatorService.create.and.returnValue(
      of({
        id: 111,
        name: createRequestWithoutAccess.name,
        phoneNumber: createRequestWithoutAccess.phoneNumber,
        birthdayDate: createRequestWithoutAccess.birthdayDate,
      }),
    );
    commentatorService.update.and.returnValue(
      of({
        id: 98765,
        name: updateRequest.name,
        phoneNumber: updateRequest.phoneNumber,
        birthdayDate: updateRequest.birthdayDate,
      }),
    );
    commentatorService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [CommentatorManagementComponent],
      providers: [{ provide: CommentatorService, useValue: commentatorService }],
    }).compileComponents();

    fixture = TestBed.createComponent(CommentatorManagementComponent);
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

  it('should load commentators on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(commentatorService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title and cadastral form fields', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar comentaristas');
    expect(text).toContain('Cadastrar comentarista');
    expect(fixture.nativeElement.querySelector('#name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#phoneNumber')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#birthdayDate')).not.toBeNull();
  });

  it('should show loading while commentators are pending', async () => {
    const pendingRequest = new Subject<CommentatorResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando comentaristas...');

    pendingRequest.next(commentators);
    pendingRequest.complete();
  });

  it('should render loaded commentators', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Maria Comentarista');
    expect(text).toContain('Joao Comentarista');
    expect(text).toContain('34999999992');
    expect(text).toContain('1991-02-11');
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum comentarista cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    commentatorService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(commentators),
    );

    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nao foi possivel carregar os comentaristas. Tente novamente.',
    );

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(commentatorService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Comentarista');
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

      expect(commentatorService.create).not.toHaveBeenCalled();
      expect(component.form.controls.name.touched).toBeTrue();
      expect(component.form.controls.phoneNumber.touched).toBeTrue();
      expect(component.form.controls.birthdayDate.touched).toBeTrue();
      expect(textContent()).toContain('Informe o nome do comentarista.');
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

      expect(commentatorService.create).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe o nome do comentarista.');
      expect(textContent()).toContain('Informe um telefone com 11 digitos.');
      expect(textContent()).toContain('Informe uma data de nascimento no passado.');
    });

    it('should create commentators without access using the expected payload', async () => {
      await setup();

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(commentatorService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
      expect(textContent()).toContain('Comentarista cadastrado com sucesso.');
      expect(component.form.controls.createAccess.value).toBeFalse();
    });

    it('should trim textual values before submitting', async () => {
      await setup();

      fixture.detectChanges();
      component.form.patchValue({
        name: '  Ana Comentarista  ',
        phoneNumber: '34999999994',
        birthdayDate: '1995-03-15',
      });
      clickButton('Cadastrar');

      expect(commentatorService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });

    it('should expose saving state while creating and prevent duplicate saves', async () => {
      const pendingSave = new Subject<CommentatorResponse>();
      await setup();
      commentatorService.create.and.returnValue(pendingSave);

      fixture.detectChanges();
      fillCadastralFields(createRequestWithoutAccess);
      clickButton('Cadastrar');
      clickButton('Cadastrar');

      expect(component.isSaving()).toBeTrue();
      expect(commentatorService.create).toHaveBeenCalledTimes(1);

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

      expect(commentatorService.create).not.toHaveBeenCalled();
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

      expect(commentatorService.create).not.toHaveBeenCalled();
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

      expect(commentatorService.create).not.toHaveBeenCalled();
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

      expect(commentatorService.create).not.toHaveBeenCalled();
    });

    it('should create commentators with access sending ROLE_OPERATOR and never ROLE_ADMIN', async () => {
      await setup();

      fixture.detectChanges();
      setCheckbox('createAccess', true);
      fixture.detectChanges();
      fillCadastralFields(createRequestWithAccess);
      component.form.patchValue({ password: '123456', confirmPassword: '123456' });
      clickButton('Cadastrar');
      fixture.detectChanges();

      expect(commentatorService.create).toHaveBeenCalledOnceWith(createRequestWithAccess);
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

      const sentRequest = commentatorService.create.calls.mostRecent().args[0] as unknown as Record<
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

      expect(commentatorService.create).toHaveBeenCalledOnceWith(createRequestWithoutAccess);
    });
  });

  it('should show friendly create validation and permission errors', async () => {
    await setup();
    commentatorService.create.and.returnValues(
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
    commentatorService.create.and.returnValue(
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
    commentatorService.create.and.returnValue(
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

  it('should enter edit mode without exposing access fields and update commentators with cadastral data only', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar comentarista');
    expect(component.editingCommentatorId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      name: 'Maria Comentarista',
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

    expect(commentatorService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
    expect(component.editingCommentatorId()).toBeNull();
    expect(textContent()).toContain('Comentarista atualizado com sucesso.');
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

    expect(commentatorService.update).toHaveBeenCalledOnceWith(98765, updateRequest);
  });

  it('should show a friendly message when the account fields are rejected on update', async () => {
    await setup();
    commentatorService.update.and.returnValue(
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
    commentatorService.update.and.returnValue(
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
    expect(commentatorService.update).toHaveBeenCalledTimes(1);
  });

  it('should cancel editing without calling the backend and restore the initial creation state', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(commentatorService.update).not.toHaveBeenCalled();
    expect(component.editingCommentatorId()).toBeNull();
    expect(textContent()).toContain('Cadastrar comentarista');
    expect(component.form.controls.createAccess.value).toBeFalse();
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.confirmPassword.value).toBe('');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    commentatorService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O comentarista solicitado nao foi encontrado.');
    expect(commentatorService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este comentarista?');
    expect(textContent()).toContain('Maria Comentarista');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(commentatorService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete a commentator after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(commentatorService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Comentarista excluido com sucesso.');
    expect(textContent()).not.toContain('Maria Comentarista');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    commentatorService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(commentatorService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors', async () => {
    await setup();
    commentatorService.delete.and.returnValues(
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
      'Nao e possivel excluir este comentarista porque ele esta vinculado a eventos.',
    );
    expect(textContent()).toContain('Maria Comentarista');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O comentarista solicitado nao foi encontrado.');
    expect(commentatorService.findAll).toHaveBeenCalledTimes(2);
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
