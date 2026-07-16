import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { CelebrationEventRequest, CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import { EventManagementComponent } from './event-management.component';

describe('EventManagementComponent', () => {
  let component: EventManagementComponent;
  let fixture: ComponentFixture<EventManagementComponent>;
  let eventService: jasmine.SpyObj<EventService>;

  const events: CelebrationEventResponse[] = [
    {
      id: 98765,
      nameMassOrEvent: 'Missa de Domingo',
      eventDate: '2026-08-02',
      eventTime: '10:30:00',
      massOrCelebration: true,
    },
    {
      id: 54321,
      nameMassOrEvent: 'Celebracao da Palavra',
      eventDate: '2026-08-08',
      eventTime: '19:45:00',
      massOrCelebration: false,
    },
  ];
  const request: CelebrationEventRequest = {
    nameMassOrEvent: 'Missa Nova',
    eventDate: '2026-09-10',
    eventTime: '18:00:00',
    massOrCelebration: true,
  };

  async function setup(response = of(events)): Promise<void> {
    eventService = jasmine.createSpyObj<EventService>('EventService', [
      'findAll',
      'create',
      'update',
      'delete',
    ]);
    eventService.findAll.and.returnValue(response);
    eventService.create.and.returnValue(
      of({
        id: 111,
        ...request,
      }),
    );
    eventService.update.and.returnValue(
      of({
        id: 98765,
        ...request,
      }),
    );
    eventService.delete.and.returnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [EventManagementComponent],
      providers: [provideRouter([]), { provide: EventService, useValue: eventService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EventManagementComponent);
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

  it('should load events on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render title, form, and scale guidance', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Gerenciar eventos');
    expect(text).toContain('Cadastrar evento');
    expect(text).toContain('Local e participantes pertencem a escala do evento');
    expect(fixture.nativeElement.querySelector('form')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#nameMassOrEvent')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#eventDate')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#eventTime')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#massOrCelebration')).not.toBeNull();
  });

  it('should show loading while events are pending', async () => {
    const pendingRequest = new Subject<CelebrationEventResponse[]>();
    await setup(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando eventos...');

    pendingRequest.next(events);
    pendingRequest.complete();
  });

  it('should render loaded events and scale links', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();
    const links = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('a'),
    ) as HTMLAnchorElement[];

    expect(text).toContain('Missa de Domingo');
    expect(text).toContain('02/08/2026');
    expect(text).toContain('10:30');
    expect(text).toContain('Missa');
    expect(text).toContain('Definido na escala');
    expect(links.some((link) => link.textContent?.includes('Ver escala'))).toBeTrue();
    expect(links.some((link) => link.textContent?.includes('Editar escala'))).toBeTrue();
    expect(links.some((link) => link.getAttribute('href') === '/app/escalas/eventos/98765')).toBeTrue();
    expect(
      links.some((link) => link.getAttribute('href') === '/app/admin/escalas/eventos/98765/editar'),
    ).toBeTrue();
  });

  it('should show an empty state', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum evento cadastrado foi encontrado.');
  });

  it('should show loading errors and retry', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));
    eventService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(events),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar os eventos. Tente novamente.');

    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Missa de Domingo');
  });

  it('should not submit invalid forms and should mark fields as touched', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eventService.create).not.toHaveBeenCalled();
    expect(component.form.controls.nameMassOrEvent.touched).toBeTrue();
    expect(component.form.controls.eventDate.touched).toBeTrue();
    expect(component.form.controls.eventTime.touched).toBeTrue();
    expect(component.form.controls.massOrCelebration.touched).toBeTrue();
    expect(textContent()).toContain('Informe o nome do evento.');
    expect(textContent()).toContain('Informe a data do evento.');
    expect(textContent()).toContain('Informe o horario do evento.');
    expect(textContent()).toContain('Informe se o evento e uma missa ou celebracao.');
  });

  it('should reject blank names and past dates before submitting', async () => {
    await setup();

    fixture.detectChanges();
    component.form.setValue({
      nameMassOrEvent: '   ',
      eventDate: '2020-01-01',
      eventTime: '18:00',
      massOrCelebration: true,
    });
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eventService.create).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe o nome do evento.');
    expect(textContent()).toContain('A data deve ser hoje ou uma data futura.');
  });

  it('should create events with the expected payload and no extra fields', async () => {
    await setup();

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(eventService.create).toHaveBeenCalledOnceWith(request);
    expect(Object.keys(eventService.create.calls.mostRecent().args[0])).toEqual([
      'nameMassOrEvent',
      'eventDate',
      'eventTime',
      'massOrCelebration',
    ]);
    expect(textContent()).toContain('Evento cadastrado com sucesso.');
    expect(textContent()).toContain('Missa Nova');
    expect(component.form.getRawValue()).toEqual({
      nameMassOrEvent: '',
      eventDate: '',
      eventTime: '',
      massOrCelebration: null,
    });
  });

  it('should prevent duplicate saves while creating', async () => {
    const pendingSave = new Subject<CelebrationEventResponse>();
    await setup();
    eventService.create.and.returnValue(pendingSave);

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    clickButton('Cadastrar');

    expect(eventService.create).toHaveBeenCalledTimes(1);

    pendingSave.next({
      id: 111,
      ...request,
    });
    pendingSave.complete();
  });

  it('should show friendly create validation, permission, and conflict errors', async () => {
    await setup();
    eventService.create.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 400 })),
      throwError(() => new HttpErrorResponse({ status: 403 })),
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    fixture.detectChanges();
    fillForm(request);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Revise os dados do evento antes de salvar.');

    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para salvar eventos.');

    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(textContent()).toContain(
      'Nao foi possivel salvar o evento devido a um conflito com os dados atuais.',
    );
  });

  it('should enter edit mode and update events', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(textContent()).toContain('Editar evento');
    expect(component.editingEventId()).toBe(98765);
    expect(component.form.getRawValue()).toEqual({
      nameMassOrEvent: 'Missa de Domingo',
      eventDate: '2026-08-02',
      eventTime: '10:30',
      massOrCelebration: true,
    });

    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(eventService.update).toHaveBeenCalledOnceWith(98765, request);
    expect(component.editingEventId()).toBeNull();
    expect(textContent()).toContain('Evento atualizado com sucesso.');
    expect(textContent()).toContain('Missa Nova');
  });

  it('should cancel editing without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    clickButton('Cancelar edicao');
    fixture.detectChanges();

    expect(eventService.update).not.toHaveBeenCalled();
    expect(component.editingEventId()).toBeNull();
    expect(textContent()).toContain('Cadastrar evento');
  });

  it('should handle update not found and generic errors', async () => {
    await setup();
    eventService.update.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();
    fillForm(request);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('O evento solicitado nao foi encontrado.');
    expect(eventService.findAll).toHaveBeenCalledTimes(2);

    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel concluir a operacao. Tente novamente.');
  });

  it('should open and cancel delete confirmation without calling the backend', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(textContent()).toContain('Deseja realmente excluir este evento?');
    expect(textContent()).toContain('Missa de Domingo');

    clickButton('Cancelar');
    fixture.detectChanges();

    expect(eventService.delete).not.toHaveBeenCalled();
    expect(component.pendingDeletion()).toBeNull();
  });

  it('should delete an event after confirmation', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(eventService.delete).toHaveBeenCalledOnceWith(98765);
    expect(textContent()).toContain('Evento excluido com sucesso.');
    expect(textContent()).not.toContain('Missa de Domingo');
  });

  it('should prevent duplicate delete calls while deleting', async () => {
    const pendingDelete = new Subject<void>();
    await setup();
    eventService.delete.and.returnValue(pendingDelete);

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();
    clickButton('Confirmar exclusao');
    clickButton('Confirmar exclusao');

    expect(eventService.delete).toHaveBeenCalledTimes(1);

    pendingDelete.next();
    pendingDelete.complete();
  });

  it('should handle delete errors and keep conflicted events listed', async () => {
    await setup();
    eventService.delete.and.returnValues(
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
      'Nao e possivel excluir este evento porque ele possui vinculos com a escala.',
    );
    expect(textContent()).toContain('Missa de Domingo');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para excluir eventos.');

    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(textContent()).toContain('O evento solicitado nao foi encontrado.');
    expect(eventService.findAll).toHaveBeenCalledTimes(2);
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

  function fillForm(value: CelebrationEventRequest): void {
    component.form.setValue({
      nameMassOrEvent: value.nameMassOrEvent,
      eventDate: value.eventDate,
      eventTime: value.eventTime.slice(0, 5),
      massOrCelebration: value.massOrCelebration,
    });
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
