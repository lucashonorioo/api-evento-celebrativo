import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import { EventListComponent } from './event-list.component';

@Component({
  selector: 'app-test-shell',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class TestShellComponent {}

@Component({
  selector: 'app-empty-test',
  standalone: true,
  template: '',
})
class EmptyTestComponent {}

const NOW = new Date(2026, 6, 20, 10, 0, 0);

describe('EventListComponent', () => {
  let fixture: ComponentFixture<EventListComponent>;
  let component: EventListComponent;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventService: jasmine.SpyObj<EventService>;

  async function setup(
    events$: Observable<CelebrationEventResponse[]> = of(defaultEvents()),
    isAdmin = false,
  ): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(events$);

    await TestBed.configureTestingModule({
      imports: [EventListComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventListComponent);
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

  it('should load events once on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should prevent a duplicate request while a load is already in progress', async () => {
    const pending = new Subject<CelebrationEventResponse[]>();
    await setup(pending.asObservable());

    fixture.detectChanges();
    component.loadEvents();
    component.loadEvents();

    expect(eventService.findAll).toHaveBeenCalledTimes(1);

    pending.next(defaultEvents());
    pending.complete();
  });

  it('should default the period filter to Próximos', async () => {
    await setup();

    fixture.detectChanges();

    expect(component.periodFilter()).toBe('upcoming');
    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should include an event happening exactly at the current moment', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).toContain(7);
  });

  it('should include a future event using HH:mm time', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).toContain(3);
  });

  it('should include a future event using HH:mm:ss time', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).toContain(4);
  });

  it('should exclude past events from the upcoming period', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).not.toContain(1);
    expect(ids()).not.toContain(2);
  });

  it('should list past events in descending order, most recent first', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('past');

    expect(ids()).toEqual([2, 1]);
  });

  it('should list upcoming events in ascending order', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should show both future and past events when the period is Todos', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');

    expect(ids()).toEqual([1, 2, 7, 3, 4, 5, 6]);
  });

  it('should not mutate the original collection returned by the service', async () => {
    const events = defaultEvents();
    const originalOrder = events.map((event) => event.id);
    await setup(of(events));

    fixture.detectChanges();
    selectPeriod('past');

    expect(events.map((event) => event.id)).toEqual(originalOrder);
  });

  it('should preserve distinct events that share the same date and time', async () => {
    await setup();

    fixture.detectChanges();

    expect(ids()).toContain(4);
    expect(ids()).toContain(5);
    expect(fixture.nativeElement.querySelectorAll('.event-card').length).toBe(5);
  });

  it('should search events by name', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('Vespertina');

    expect(ids()).toEqual([6]);
  });

  it('should search case-insensitively', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('VESPERTINA');

    expect(ids()).toEqual([6]);
  });

  it('should trim surrounding spaces from the search term', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('   Vespertina   ');

    expect(ids()).toEqual([6]);
  });

  it('should normalize accents when searching', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('Acao de Gracas');

    expect(ids()).toEqual([5]);
  });

  it('should filter by Missa', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');

    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should filter by Celebração', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setType('celebration');

    expect(ids()).toEqual([2, 4]);
  });

  it('should combine period, search and type filters', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');
    setSearch('Missa de');

    expect(ids()).toEqual([1, 5]);
  });

  it('should clear filters without triggering a new HTTP request', async () => {
    await setup();

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
    expect(component.allEvents().length).toBe(defaultEvents().length);
  });

  it('should show the singular count for a single result', async () => {
    await setup(of([createEvent({ id: 1, eventDate: '2026-07-25', eventTime: '09:00' })]));

    fixture.detectChanges();

    expect(textContent()).toContain('1 evento encontrado');
  });

  it('should show the plural count for multiple results', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');

    expect(textContent()).toContain('7 eventos encontrados');
  });

  it('should show the message for no events registered by the API', async () => {
    await setup(of([]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum evento foi cadastrado.');
    expect(emptyStateClearButton()).toBeNull();
  });

  it('should show the message for no upcoming events when past events exist', async () => {
    await setup(of([createEvent({ id: 1, eventDate: '2026-07-18', eventTime: '10:00' })]));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum próximo evento foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the message for no past events', async () => {
    await setup(of([createEvent({ id: 3, eventDate: '2026-07-21', eventTime: '08:00' })]));

    fixture.detectChanges();
    selectPeriod('past');

    expect(textContent()).toContain('Nenhum evento passado foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the generic filtered-empty message when filters exclude all results', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');
    setSearch('nome que nao existe');

    expect(textContent()).toContain('Nenhum evento corresponde aos filtros informados.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should render the loading state while events are being requested', async () => {
    const pending = new Subject<CelebrationEventResponse[]>();
    await setup(pending.asObservable());

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando eventos...');

    pending.next(defaultEvents());
    pending.complete();
  });

  it('should render an error state when the request fails', async () => {
    await setup(throwError(() => new Error('Request failed')));

    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Não foi possível carregar os eventos. Tente novamente.');
    expect(textContent()).toContain('Não foi possível carregar os eventos.');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('should retry loading while preserving the current filters', async () => {
    await setup(throwError(() => new Error('Request failed')));

    fixture.detectChanges();
    selectPeriod('all');
    setType('mass');

    eventService.findAll.and.returnValue(of(defaultEvents()));
    retryButtonEl().click();
    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(component.periodFilter()).toBe('all');
    expect(component.typeFilter()).toBe('mass');
    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should not render data outside of the CelebrationEventResponse contract', async () => {
    await setup();

    fixture.detectChanges();
    selectPeriod('all');

    const text = textContent();

    expect(text).not.toContain('Local');
    expect(text).not.toContain('escalados');
    expect(text).not.toContain('participantes');
    expect(text).not.toContain('eventId');
    expect(text).not.toContain('locationId');
  });

  it('should expose accessible labels, semantic period grouping and a live region', async () => {
    await setup();

    fixture.detectChanges();

    const searchInput = fixture.nativeElement.querySelector('#event-search');
    const searchLabel = fixture.nativeElement.querySelector('label[for="event-search"]');
    const typeSelect = fixture.nativeElement.querySelector('#event-type');
    const typeLabel = fixture.nativeElement.querySelector('label[for="event-type"]');
    const fieldset = fixture.nativeElement.querySelector('fieldset.events__period');
    const legend = fieldset?.querySelector('legend');
    const liveRegion = fixture.nativeElement.querySelector('[aria-live="polite"]');
    const clearButton = (
      Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]
    ).find((button) => button.textContent?.trim() === 'Limpar filtros');

    expect(searchInput).not.toBeNull();
    expect(searchLabel).not.toBeNull();
    expect(typeSelect).not.toBeNull();
    expect(typeLabel).not.toBeNull();
    expect(fieldset).not.toBeNull();
    expect(legend?.textContent).toContain('Período');
    expect(liveRegion).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('h1').length).toBe(1);
    expect(clearButton?.getAttribute('type')).toBe('button');
  });

  it('should render public detail links relative to the public list route', async () => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(true);
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(futureEvents()));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', component: EventListComponent },
          { path: 'eventos/:id', component: EmptyTestComponent },
        ]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/eventos');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/eventos/1');
    expect(link?.textContent).toContain('Ver detalhes');
    expect(harness.routeNativeElement?.querySelector('.page-action')).toBeNull();
  });

  it('should render authenticated detail links relative to the authenticated list route', async () => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(false);
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(futureEvents()));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'app',
            component: TestShellComponent,
            children: [
              { path: 'eventos', component: EventListComponent },
              { path: 'eventos/:id', component: EmptyTestComponent },
            ],
          },
        ]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/app/eventos');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/app/eventos/1');
  });

  it('should render event management action for admins in the authenticated list route', async () => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(true);
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(futureEvents()));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'app',
            component: TestShellComponent,
            children: [
              { path: 'eventos', component: EventListComponent },
              { path: 'admin/eventos', component: EmptyTestComponent },
            ],
          },
        ]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/app/eventos');
    const link = harness.routeNativeElement?.querySelector('.page-action') as HTMLAnchorElement;

    expect(link.textContent).toContain('Gerenciar eventos');
    expect(link.getAttribute('href')).toBe('/app/admin/eventos');
    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
  });

  it('should hide event management action for operators in the authenticated list route', async () => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(false);
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(futureEvents()));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'app',
            component: TestShellComponent,
            children: [{ path: 'eventos', component: EventListComponent }],
          },
        ]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/app/eventos');

    expect(harness.routeNativeElement?.querySelector('.page-action')).toBeNull();
  });

  function ids(): number[] {
    return component.visibleEvents().map((event) => event.id);
  }

  function selectPeriod(value: 'upcoming' | 'past' | 'all'): void {
    const radio = fixture.nativeElement.querySelector(
      `input[name="event-period"][value="${value}"]`,
    ) as HTMLInputElement;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function setSearch(value: string): void {
    const input = fixture.nativeElement.querySelector('#event-search') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setType(value: 'all' | 'mass' | 'celebration'): void {
    const select = fixture.nativeElement.querySelector('#event-type') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function emptyStateClearButton(): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector('.events__feedback .events__button--secondary');
  }

  function retryButtonEl(): HTMLButtonElement {
    return fixture.nativeElement.querySelector(
      '.events__feedback--error .events__button',
    ) as HTMLButtonElement;
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});

function createEvent(overrides: Partial<CelebrationEventResponse> = {}): CelebrationEventResponse {
  return {
    id: 1,
    nameMassOrEvent: 'Evento',
    eventDate: '2026-07-25',
    eventTime: '09:00:00',
    massOrCelebration: true,
    ...overrides,
  };
}

function defaultEvents(): CelebrationEventResponse[] {
  return [
    createEvent({
      id: 1,
      nameMassOrEvent: 'Missa de Domingo Passada',
      eventDate: '2026-07-18',
      eventTime: '10:00:00',
      massOrCelebration: true,
    }),
    createEvent({
      id: 2,
      nameMassOrEvent: 'Celebração da Palavra Passada',
      eventDate: '2026-07-19',
      eventTime: '18:00:00',
      massOrCelebration: false,
    }),
    createEvent({
      id: 3,
      nameMassOrEvent: 'Missa da Comunidade',
      eventDate: '2026-07-21',
      eventTime: '08:00',
      massOrCelebration: true,
    }),
    createEvent({
      id: 4,
      nameMassOrEvent: 'Celebração Ecumênica',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: false,
    }),
    createEvent({
      id: 5,
      nameMassOrEvent: 'Missa de Ação de Graças',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: true,
    }),
    createEvent({
      id: 6,
      nameMassOrEvent: 'Missa Vespertina',
      eventDate: '2026-07-30',
      eventTime: '19:00',
      massOrCelebration: true,
    }),
    createEvent({
      id: 7,
      nameMassOrEvent: 'Missa no Instante Atual',
      eventDate: '2026-07-20',
      eventTime: '10:00',
      massOrCelebration: true,
    }),
  ];
}

function futureEvents(): CelebrationEventResponse[] {
  return [
    createEvent({
      id: 1,
      nameMassOrEvent: 'Missa de Domingo',
      eventDate: '2026-07-25',
      eventTime: '10:30:00',
      massOrCelebration: true,
    }),
  ];
}
