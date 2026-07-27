import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { CelebrationEventRequest, CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import { EventManagementComponent } from './event-management.component';

const NOW = new Date(2026, 6, 20, 10, 0, 0);

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

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(NOW);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
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

  it('should prevent a duplicate request while a load is already in progress', async () => {
    const pendingRequest = new Subject<CelebrationEventResponse[]>();
    await setup(pendingRequest.asObservable());

    fixture.detectChanges();
    component.loadEvents();
    component.loadEvents();

    expect(eventService.findAll).toHaveBeenCalledTimes(1);

    pendingRequest.next(events);
    pendingRequest.complete();
  });

  it('should retry loading while preserving the current filters', async () => {
    await setup(throwError(() => new HttpErrorResponse({ status: 500 })));

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');

    eventService.findAll.and.returnValue(of(filterTestEvents()));
    clickButton('Tentar novamente');
    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(component.periodFilter()).toBe('all');
    expect(component.typeFilter()).toBe('mass');
    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should default the period filter to Próximos and exclude past events', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(component.periodFilter()).toBe('upcoming');
    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should include an event happening exactly at the current moment', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).toContain(7);
  });

  it('should interpret an HH:mm event time with zero seconds', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).toContain(3);
  });

  it('should interpret an HH:mm:ss event time using its seconds', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).toContain(4);
  });

  it('should exclude past events from the upcoming period', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).not.toContain(1);
    expect(ids()).not.toContain(2);
  });

  it('should list upcoming events in ascending order', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should list past events in descending order, most recent first', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('past');

    expect(ids()).toEqual([2, 1]);
  });

  it('should show both future and past events when the period is Todos', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');

    expect(ids()).toEqual([1, 2, 7, 3, 4, 5, 6]);
  });

  it('should not mutate the original events collection while filtering', async () => {
    const customEvents = filterTestEvents();
    const originalOrder = customEvents.map((event) => event.id);
    await setup(of(customEvents));

    fixture.detectChanges();
    selectPeriod('past');

    expect(customEvents.map((event) => event.id)).toEqual(originalOrder);
    expect(component.events().map((event) => event.id)).toEqual(originalOrder);
  });

  it('should preserve distinct events that share the same date and time', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();

    expect(ids()).toContain(4);
    expect(ids()).toContain(5);
  });

  it('should search events by name', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('Vespertina');

    expect(ids()).toEqual([6]);
  });

  it('should search case-insensitively', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('VESPERTINA');

    expect(ids()).toEqual([6]);
  });

  it('should trim surrounding spaces from the search term', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('   Vespertina   ');

    expect(ids()).toEqual([6]);
  });

  it('should normalize accents when searching', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('Acao de Gracas');

    expect(ids()).toEqual([5]);
  });

  it('should filter by Missa', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');

    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should filter by Celebração', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setType('celebration');

    expect(ids()).toEqual([2, 4]);
  });

  it('should combine period, search and type filters', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');
    setSearch('Missa de');

    expect(ids()).toEqual([1, 5]);
  });

  it('should clear filters without triggering a new HTTP request', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('past');
    setSearch('Vespertina');
    setType('celebration');

    component.clearFilters();
    fixture.detectChanges();

    expect(component.periodFilter()).toBe('upcoming');
    expect(component.searchTerm()).toBe('');
    expect(component.typeFilter()).toBe('all');
    expect(eventService.findAll).toHaveBeenCalledTimes(1);
    expect(component.events().length).toBe(filterTestEvents().length);
  });

  it('should show the singular result count', async () => {
    await setup(
      of([
        {
          id: 1,
          nameMassOrEvent: 'Missa Unica',
          eventDate: '2026-07-25',
          eventTime: '09:00',
          massOrCelebration: true,
        },
      ]),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('1 evento encontrado');
  });

  it('should show the plural result count', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');

    expect(textContent()).toContain('7 eventos encontrados');
  });

  it('should show the message for an empty API collection', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum evento cadastrado foi encontrado.');
    expect(emptyStateClearButton()).toBeNull();
  });

  it('should show the message for no upcoming events when past events exist', async () => {
    await setup(
      of([
        {
          id: 1,
          nameMassOrEvent: 'Missa Passada',
          eventDate: '2026-07-18',
          eventTime: '10:00',
          massOrCelebration: true,
        },
      ]),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum próximo evento cadastrado foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the message for no past events', async () => {
    await setup(
      of([
        {
          id: 3,
          nameMassOrEvent: 'Missa Futura',
          eventDate: '2026-07-21',
          eventTime: '08:00',
          massOrCelebration: true,
        },
      ]),
    );

    fixture.detectChanges();
    selectPeriod('past');

    expect(textContent()).toContain('Nenhum evento passado foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the generic filtered-empty message when search or type exclude all results', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('nome que nao existe');

    expect(textContent()).toContain('Nenhum evento corresponde aos filtros informados.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should keep an active period filter after registering a new event, without forcing it to appear', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('past');

    const pastRequest: CelebrationEventRequest = {
      nameMassOrEvent: 'Evento Futuro Novo',
      eventDate: '2026-09-10',
      eventTime: '18:00:00',
      massOrCelebration: true,
    };
    eventService.create.and.returnValue(of({ id: 999, ...pastRequest }));

    fillForm(pastRequest);
    clickButton('Cadastrar');
    fixture.detectChanges();

    expect(component.periodFilter()).toBe('past');
    expect(component.events().some((event) => event.id === 999)).toBeTrue();
    expect(ids()).not.toContain(999);
    expect(textContent()).toContain('Evento cadastrado com sucesso.');
  });

  it('should update an event while preserving active filters', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');

    const updateRequest: CelebrationEventRequest = {
      nameMassOrEvent: 'Missa da Comunidade Atualizada',
      eventDate: '2026-07-21',
      eventTime: '08:00:00',
      massOrCelebration: true,
    };
    eventService.update.and.returnValue(of({ id: 3, ...updateRequest }));

    startEditingEvent(3);
    fillForm(updateRequest);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(component.periodFilter()).toBe('all');
    expect(component.typeFilter()).toBe('mass');
    expect(textContent()).toContain('Missa da Comunidade Atualizada');
  });

  it('should let an updated event disappear when it no longer matches the active filters', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setType('celebration');

    const updateToMass: CelebrationEventRequest = {
      nameMassOrEvent: 'Celebracao Convertida em Missa',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: true,
    };
    eventService.update.and.returnValue(of({ id: 4, ...updateToMass }));

    startEditingEvent(4);
    fillForm(updateToMass);
    clickButton('Salvar alteracoes');
    fixture.detectChanges();

    expect(ids()).not.toContain(4);
    expect(component.events().find((event) => event.id === 4)?.massOrCelebration).toBeTrue();
  });

  it('should delete an event while preserving active filters', async () => {
    await setup(of(filterTestEvents()));

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('Vespertina');

    startDeletionByEventId(6);
    clickButton('Confirmar exclusao');
    fixture.detectChanges();

    expect(eventService.delete).toHaveBeenCalledOnceWith(6);
    expect(component.periodFilter()).toBe('all');
    expect(component.searchTerm()).toBe('Vespertina');
    expect(component.events().some((event) => event.id === 6)).toBeFalse();
  });

  it('should not clear an in-progress edit when filters change', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Editar');
    fixture.detectChanges();

    expect(component.editingEventId()).toBe(98765);

    selectPeriod('all');
    setSearch('teste');
    setType('mass');

    expect(component.editingEventId()).toBe(98765);
    expect(component.form.getRawValue().nameMassOrEvent).toBe('Missa de Domingo');
    expect(eventService.update).not.toHaveBeenCalled();
  });

  it('should not cancel a pending delete confirmation when filters change', async () => {
    await setup();

    fixture.detectChanges();
    clickButton('Excluir');
    fixture.detectChanges();

    expect(component.pendingDeletion()?.id).toBe(98765);

    selectPeriod('past');
    setType('celebration');

    expect(component.pendingDeletion()?.id).toBe(98765);
    expect(eventService.delete).not.toHaveBeenCalled();
  });

  it('should expose accessible labels, semantic period grouping and a live region for filters', async () => {
    await setup();

    fixture.detectChanges();

    const searchInput = fixture.nativeElement.querySelector('#event-management-search');
    const searchLabel = fixture.nativeElement.querySelector('label[for="event-management-search"]');
    const typeSelect = fixture.nativeElement.querySelector('#event-management-type');
    const typeLabel = fixture.nativeElement.querySelector('label[for="event-management-type"]');
    const fieldset = fixture.nativeElement.querySelector('fieldset.event-management__period');
    const legend = fieldset?.querySelector('legend');
    const liveRegions = fixture.nativeElement.querySelectorAll('[aria-live="polite"]');
    const clearButton = (
      Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((button) => button.textContent?.trim() === 'Limpar filtros');

    expect(searchInput).not.toBeNull();
    expect(searchLabel).not.toBeNull();
    expect(typeSelect).not.toBeNull();
    expect(typeLabel).not.toBeNull();
    expect(fieldset).not.toBeNull();
    expect(legend?.textContent).toContain('Período');
    expect(liveRegions.length).toBeGreaterThan(0);
    expect(clearButton?.getAttribute('type')).toBe('button');
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

  function ids(): number[] {
    return component.visibleEvents().map((event) => event.id);
  }

  function selectPeriod(value: 'upcoming' | 'past' | 'all'): void {
    const radio = fixture.nativeElement.querySelector(
      `input[name="event-management-period"][value="${value}"]`,
    ) as HTMLInputElement;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function setSearch(value: string): void {
    const input = fixture.nativeElement.querySelector('#event-management-search') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setType(value: 'all' | 'mass' | 'celebration'): void {
    const select = fixture.nativeElement.querySelector('#event-management-type') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function emptyStateClearButton(): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector(
      '.event-management__feedback-block .event-management__button--secondary',
    );
  }

  function startEditingEvent(id: number): void {
    const event = component.events().find((item) => item.id === id);

    if (event === undefined) {
      fail(`Event with id ${id} not found`);
      return;
    }

    component.startEditing(event);
    fixture.detectChanges();
  }

  function startDeletionByEventId(id: number): void {
    const event = component.events().find((item) => item.id === id);

    if (event === undefined) {
      fail(`Event with id ${id} not found`);
      return;
    }

    component.requestDeletion(event);
    fixture.detectChanges();
  }
});

function filterTestEvents(): CelebrationEventResponse[] {
  return [
    {
      id: 1,
      nameMassOrEvent: 'Missa de Domingo Passada',
      eventDate: '2026-07-18',
      eventTime: '10:00:00',
      massOrCelebration: true,
    },
    {
      id: 2,
      nameMassOrEvent: 'Celebracao da Palavra Passada',
      eventDate: '2026-07-19',
      eventTime: '18:00:00',
      massOrCelebration: false,
    },
    {
      id: 3,
      nameMassOrEvent: 'Missa da Comunidade',
      eventDate: '2026-07-21',
      eventTime: '08:00',
      massOrCelebration: true,
    },
    {
      id: 4,
      nameMassOrEvent: 'Celebracao Ecumenica',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: false,
    },
    {
      id: 5,
      nameMassOrEvent: 'Missa de Ação de Graças',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: true,
    },
    {
      id: 6,
      nameMassOrEvent: 'Missa Vespertina',
      eventDate: '2026-07-30',
      eventTime: '19:00',
      massOrCelebration: true,
    },
    {
      id: 7,
      nameMassOrEvent: 'Missa no Instante Atual',
      eventDate: '2026-07-20',
      eventTime: '10:00',
      massOrCelebration: true,
    },
  ];
}
