import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { ReaderRequest, ReaderResponse } from '../reader.models';
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
      phoneNumber: '34999999991',
      birthdayDate: '1990-01-10',
    },
    {
      id: 54321,
      name: 'Joao Leitor',
      phoneNumber: '34999999992',
      birthdayDate: '1988-05-20',
    },
  ];
  const request: ReaderRequest = {
    name: 'Ana Leitora',
    phoneNumber: '34999999993',
    birthdayDate: '1995-03-15',
    password: '123456',
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
        name: request.name,
        phoneNumber: request.phoneNumber,
        birthdayDate: request.birthdayDate,
      }),
    );
    readerService.update.and.returnValue(
      of({
        id: 98765,
        name: request.name,
        phoneNumber: request.phoneNumber,
        birthdayDate: request.birthdayDate,
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

  it('should render title and form fields', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar leitores');
    expect(text).toContain('Cadastrar leitor');
    expect(fixture.nativeElement.querySelector('#name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#phoneNumber')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#birthdayDate')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#password')).not.toBeNull();
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
    expect(text).toContain('34999999991');
    expect(text).toContain('1990-01-10');
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

  it('should not submit invalid forms and should mark fields as touched', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(readerService.create).not.toHaveBeenCalled();
    expect(component.form.controls.name.touched).toBeTrue();
    expect(component.form.controls.phoneNumber.touched).toBeTrue();
    expect(component.form.controls.birthdayDate.touched).toBeTrue();
    expect(component.form.controls.password.touched).toBeTrue();
    expect(textContent()).toContain('Informe o nome do leitor.');
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

    expect(readerService.create).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe o nome do leitor.');
    expect(textContent()).toContain('Informe um telefone com 11 digitos.');
    expect(textContent()).toContain('Informe uma data de nascimento no passado.');
    expect(textContent()).toContain('Informe uma senha com pelo menos 6 caracteres.');
  });

  it('should create readers with the expected payload', async () => {
    await setup();

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(readerService.create).toHaveBeenCalledOnceWith(request);
    expect(textContent()).toContain('Leitor cadastrado com sucesso.');
    expect(textContent()).toContain('Ana Leitora');
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
      name: '  Ana Leitora  ',
      phoneNumber: '34999999993',
      birthdayDate: '1995-03-15',
      password: '123456',
    });
    clickButton('Cadastrar');

    expect(readerService.create).toHaveBeenCalledOnceWith(request);
  });

  it('should expose saving state while creating and prevent duplicate saves', async () => {
    const pendingSave = new Subject<ReaderResponse>();
    await setup();
    readerService.create.and.returnValue(pendingSave);

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    clickButton('Cadastrar');

    expect(component.isSaving()).toBeTrue();
    expect(readerService.create).toHaveBeenCalledTimes(1);

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
    readerService.create.and.returnValues(
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

  it('should enter edit mode and update readers', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar leitor');
    expect(component.editingReaderId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      name: 'Maria Leitora',
      phoneNumber: '34999999991',
      birthdayDate: '1990-01-10',
      password: '',
    });

    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(readerService.update).toHaveBeenCalledOnceWith(98765, request);
    expect(component.editingReaderId()).toBeNull();
    expect(textContent()).toContain('Leitor atualizado com sucesso.');
    expect(textContent()).toContain('Ana Leitora');
  });

  it('should cancel editing without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(readerService.update).not.toHaveBeenCalled();
    expect(component.editingReaderId()).toBeNull();
    expect(textContent()).toContain('Cadastrar leitor');
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
    fillForm(request);
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

  it('should not expose identifiers, JSON, tokens, or unknown data', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).not.toContain('98765');
    expect(text).not.toContain('54321');
    expect(text).not.toContain('{');
    expect(text).not.toContain('access_token');
    expect(text).not.toContain('Bearer');
    expect(text).not.toContain('undefined');
    expect(text).not.toContain('null');
  });

  function fillForm(value: ReaderRequest): void {
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
