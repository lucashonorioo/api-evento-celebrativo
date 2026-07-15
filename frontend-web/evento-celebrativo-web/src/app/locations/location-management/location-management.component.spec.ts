import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { LocationRequest, LocationResponse } from '../location.models';
import { LocationService } from '../location.service';
import { LocationManagementComponent } from './location-management.component';

describe('LocationManagementComponent', () => {
  let component: LocationManagementComponent;
  let fixture: ComponentFixture<LocationManagementComponent>;
  let locationService: jasmine.SpyObj<LocationService>;

  const locations: LocationResponse[] = [
    {
      id: 98765,
      churchName: 'Igreja Matriz',
      address: 'Rua Central',
    },
    {
      id: 54321,
      churchName: 'Capela Santa Luzia',
      address: 'Rua da Capela',
    },
  ];
  const request: LocationRequest = {
    churchName: 'Igreja Nova',
    address: 'Rua Nova, 100',
  };

  async function setup(response = of(locations)): Promise<void> {
    locationService = jasmine.createSpyObj<LocationService>('LocationService', [
      'findAll',
      'create',
      'update',
      'delete',
    ]);
    locationService.findAll.and.returnValue(response);
    locationService.create.and.returnValue(
      of({
        id: 111,
        ...request,
      }),
    );
    locationService.update.and.returnValue(
      of({
        id: 98765,
        ...request,
      }),
    );
    locationService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [LocationManagementComponent],
      providers: [{ provide: LocationService, useValue: locationService }],
    }).compileComponents();

    fixture = TestBed.createComponent(LocationManagementComponent);
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

  it('should load locations on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(locationService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title and form', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar locais');
    expect(text).toContain('Cadastrar local');
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#churchName')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#address')).not.toBeNull();
  });

  it('should show loading while locations are pending', async () => {
    const pendingRequest = new Subject<LocationResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando locais...');

    pendingRequest.next(locations);
    pendingRequest.complete();
  });

  it('should render loaded locations', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Igreja Matriz');
    expect(text).toContain('Rua Central');
    expect(text).toContain('Capela Santa Luzia');
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum local cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    locationService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(locations),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar os locais. Tente novamente.');

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(locationService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Igreja Matriz');
  });

  it('should not submit invalid forms and should mark fields as touched', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(locationService.create).not.toHaveBeenCalled();
    expect(component.form.controls.churchName.touched).toBeTrue();
    expect(component.form.controls.address.touched).toBeTrue();
    expect(textContent()).toContain('Informe o nome do local.');
    expect(textContent()).toContain('Informe o endereco.');
  });

  it('should reject blank values before submitting', async () => {
    await setup();

    fixture.detectChanges();
    component.form.setValue({
      churchName: '   ',
      address: '   ',
    });
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(locationService.create).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe o nome do local.');
    expect(textContent()).toContain('Informe o endereco.');
  });

  it('should create locations with the expected payload', async () => {
    await setup();

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(locationService.create).toHaveBeenCalledOnceWith(request);
    expect(textContent()).toContain('Local cadastrado com sucesso.');
    expect(textContent()).toContain('Igreja Nova');
    expect(component.form.getRawValue()).toEqual({
      churchName: '',
      address: '',
    });
  });

  it('should expose saving state while creating', async () => {
    const pendingSave = new Subject<LocationResponse>();
    await setup();
    locationService.create.and.returnValue(pendingSave);

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(component.isSaving()).toBeTrue();

    pendingSave.next({
      id: 111,
      ...request,
    });
    pendingSave.complete();
  });

  it('should show friendly create validation and permission errors', async () => {
    await setup();
    locationService.create.and.returnValues(
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

  it('should enter edit mode and update locations', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar local');
    expect(component.editingLocationId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      churchName: 'Igreja Matriz',
      address: 'Rua Central',
    });

    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(locationService.update).toHaveBeenCalledOnceWith(98765, request);
    expect(component.editingLocationId()).toBeNull();
    expect(textContent()).toContain('Local atualizado com sucesso.');
    expect(textContent()).toContain('Igreja Nova');
  });

  it('should cancel editing without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(locationService.update).not.toHaveBeenCalled();
    expect(component.editingLocationId()).toBeNull();
    expect(textContent()).toContain('Cadastrar local');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    locationService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O local solicitado nao foi encontrado.');
    expect(locationService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este local?');
    expect(textContent()).toContain('Igreja Matriz');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(locationService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete a location after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(locationService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Local excluido com sucesso.');
    expect(textContent()).not.toContain('Igreja Matriz');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    locationService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(locationService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors', async () => {
    await setup();
    locationService.delete.and.returnValues(
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
      'Nao e possivel excluir este local porque ele esta vinculado a outros registros.',
    );
    expect(textContent()).toContain('Igreja Matriz');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para realizar esta operacao.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O local solicitado nao foi encontrado.');
    expect(locationService.findAll).toHaveBeenCalledTimes(2);
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

  function fillForm(value: LocationRequest): void {
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
