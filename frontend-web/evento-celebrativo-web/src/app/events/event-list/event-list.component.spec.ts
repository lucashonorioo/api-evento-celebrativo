import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
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
  let harness: RouterTestingHarness;
  let component: EventListComponent;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventService: jasmine.SpyObj<EventService>;
  let router: Router;
  let location: Location;
  let navigateSpy: jasmine.Spy;

  async function setup(
    url = '/eventos',
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
      providers: [
        provideRouter([
          { path: 'eventos', component: EventListComponent },
          { path: 'eventos/:id', component: EmptyTestComponent },
        ]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    navigateSpy = spyOn(router, 'navigate').and.callThrough();

    harness = await RouterTestingHarness.create(url);
    component = harness.routeDebugElement?.componentInstance as EventListComponent;
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

    expect(component).toBeTruthy();
  });

  it('should load events once on initialization', async () => {
    await setup();

    expect(eventService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should prevent a duplicate request while a load is already in progress', async () => {
    const pending = new Subject<CelebrationEventResponse[]>();
    await setup('/eventos', pending.asObservable());

    component.loadEvents();
    component.loadEvents();

    expect(eventService.findAll).toHaveBeenCalledTimes(1);

    pending.next(defaultEvents());
    pending.complete();
  });

  it('should default the period filter to Próximos', async () => {
    await setup();

    expect(component.periodFilter()).toBe('upcoming');
    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should include an event happening exactly at the current moment', async () => {
    await setup();

    expect(ids()).toContain(7);
  });

  it('should include a future event using HH:mm time', async () => {
    await setup();

    expect(ids()).toContain(3);
  });

  it('should include a future event using HH:mm:ss time', async () => {
    await setup();

    expect(ids()).toContain(4);
  });

  it('should exclude past events from the upcoming period', async () => {
    await setup();

    expect(ids()).not.toContain(1);
    expect(ids()).not.toContain(2);
  });

  it('should list past events in descending order, most recent first', async () => {
    await setup();

    selectPeriod('past');

    expect(ids()).toEqual([2, 1]);
  });

  it('should list upcoming events in ascending order', async () => {
    await setup();

    expect(ids()).toEqual([7, 3, 4, 5, 6]);
  });

  it('should show both future and past events when the period is Todos', async () => {
    await setup();

    selectPeriod('all');

    expect(ids()).toEqual([1, 2, 7, 3, 4, 5, 6]);
  });

  it('should not mutate the original collection returned by the service', async () => {
    const events = defaultEvents();
    const originalOrder = events.map((event) => event.id);
    await setup('/eventos', of(events));

    selectPeriod('past');

    expect(events.map((event) => event.id)).toEqual(originalOrder);
  });

  it('should preserve distinct events that share the same date and time', async () => {
    await setup();

    expect(ids()).toContain(4);
    expect(ids()).toContain(5);
    expect(harness.routeNativeElement?.querySelectorAll('.event-card').length).toBe(5);
  });

  it('should search events by name', async () => {
    await setup();

    selectPeriod('all');
    setSearch('Vespertina');

    expect(ids()).toEqual([6]);
  });

  it('should search case-insensitively', async () => {
    await setup();

    selectPeriod('all');
    setSearch('VESPERTINA');

    expect(ids()).toEqual([6]);
  });

  it('should trim surrounding spaces from the search term', async () => {
    await setup();

    selectPeriod('all');
    setSearch('   Vespertina   ');

    expect(ids()).toEqual([6]);
  });

  it('should normalize accents when searching', async () => {
    await setup();

    selectPeriod('all');
    setSearch('Acao de Gracas');

    expect(ids()).toEqual([5]);
  });

  it('should filter by Missa', async () => {
    await setup();

    selectPeriod('all');
    setType('mass');

    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should filter by Celebração', async () => {
    await setup();

    selectPeriod('all');
    setType('celebration');

    expect(ids()).toEqual([2, 4]);
  });

  it('should combine period, search and type filters', async () => {
    await setup();

    selectPeriod('all');
    setType('mass');
    setSearch('Missa de');

    expect(ids()).toEqual([1, 5]);
  });

  it('should clear filters without triggering a new HTTP request', async () => {
    await setup();

    selectPeriod('past');
    setSearch('Vespertina');
    setType('celebration');

    component.clearFilters();
    harness.detectChanges();

    expect(component.periodFilter()).toBe('upcoming');
    expect(component.searchTerm()).toBe('');
    expect(component.typeFilter()).toBe('all');
    expect(eventService.findAll).toHaveBeenCalledTimes(1);
    expect(component.allEvents().length).toBe(defaultEvents().length);
  });

  it('should show the singular count for a single result', async () => {
    await setup('/eventos', of([createEvent({ id: 1, eventDate: '2026-07-25', eventTime: '09:00' })]));

    expect(textContent()).toContain('1 evento encontrado');
  });

  it('should show the plural count for multiple results', async () => {
    await setup();

    selectPeriod('all');

    expect(textContent()).toContain('7 eventos encontrados');
  });

  it('should show the message for no events registered by the API', async () => {
    await setup('/eventos', of([]));

    expect(textContent()).toContain('Nenhum evento foi cadastrado.');
    expect(emptyStateClearButton()).toBeNull();
  });

  it('should show the message for no upcoming events when past events exist', async () => {
    await setup(
      '/eventos',
      of([createEvent({ id: 1, eventDate: '2026-07-18', eventTime: '10:00' })]),
    );

    expect(textContent()).toContain('Nenhum próximo evento foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the message for no past events', async () => {
    await setup(
      '/eventos',
      of([createEvent({ id: 3, eventDate: '2026-07-21', eventTime: '08:00' })]),
    );

    selectPeriod('past');

    expect(textContent()).toContain('Nenhum evento passado foi encontrado.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should show the generic filtered-empty message when filters exclude all results', async () => {
    await setup();

    selectPeriod('all');
    setSearch('nome que nao existe');

    expect(textContent()).toContain('Nenhum evento corresponde aos filtros informados.');
    expect(emptyStateClearButton()).not.toBeNull();
  });

  it('should render the loading state while events are being requested', async () => {
    const pending = new Subject<CelebrationEventResponse[]>();
    await setup('/eventos', pending.asObservable());

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando eventos...');

    pending.next(defaultEvents());
    pending.complete();
  });

  it('should render an error state when the request fails', async () => {
    await setup('/eventos', throwError(() => new Error('Request failed')));

    expect(component.errorMessage()).toBe('Não foi possível carregar os eventos. Tente novamente.');
    expect(textContent()).toContain('Não foi possível carregar os eventos.');
    expect(harness.routeNativeElement?.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('should retry loading while preserving the current filters', async () => {
    await setup('/eventos', throwError(() => new Error('Request failed')));

    selectPeriod('all');
    setType('mass');

    eventService.findAll.and.returnValue(of(defaultEvents()));
    retryButtonEl().click();
    harness.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(component.periodFilter()).toBe('all');
    expect(component.typeFilter()).toBe('mass');
    expect(ids()).toEqual([1, 7, 3, 5, 6]);
  });

  it('should not render data outside of the CelebrationEventResponse contract', async () => {
    await setup();

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

    const searchInput = harness.routeNativeElement?.querySelector('#event-search');
    const searchLabel = harness.routeNativeElement?.querySelector('label[for="event-search"]');
    const typeSelect = harness.routeNativeElement?.querySelector('#event-type');
    const typeLabel = harness.routeNativeElement?.querySelector('label[for="event-type"]');
    const fieldset = harness.routeNativeElement?.querySelector('fieldset.events__period');
    const legend = fieldset?.querySelector('legend');
    const liveRegion = harness.routeNativeElement?.querySelector('[aria-live="polite"]');
    const clearButton = (
      Array.from(harness.routeNativeElement?.querySelectorAll('button') ?? []) as HTMLButtonElement[]
    ).find((button) => button.textContent?.trim() === 'Limpar filtros');

    expect(searchInput).not.toBeNull();
    expect(searchLabel).not.toBeNull();
    expect(typeSelect).not.toBeNull();
    expect(typeLabel).not.toBeNull();
    expect(fieldset).not.toBeNull();
    expect(legend?.textContent).toContain('Período');
    expect(liveRegion).not.toBeNull();
    expect(harness.routeNativeElement?.querySelectorAll('h1').length).toBe(1);
    expect(clearButton?.getAttribute('type')).toBe('button');
  });

  describe('query parameter restoration', () => {
    it('should use default filters when no query parameters are present', async () => {
      await setup('/eventos');

      expect(component.periodFilter()).toBe('upcoming');
      expect(component.searchTerm()).toBe('');
      expect(component.typeFilter()).toBe('all');
    });

    it('should restore Passados from period=past', async () => {
      await setup('/eventos?period=past');

      expect(component.periodFilter()).toBe('past');
      expect(ids()).toEqual([2, 1]);
    });

    it('should restore Todos from period=all', async () => {
      await setup('/eventos?period=all');

      expect(component.periodFilter()).toBe('all');
      expect(ids()).toEqual([1, 2, 7, 3, 4, 5, 6]);
    });

    it('should restore Missa from type=mass', async () => {
      await setup('/eventos?period=all&type=mass');

      expect(component.typeFilter()).toBe('mass');
      expect(ids()).toEqual([1, 7, 3, 5, 6]);
    });

    it('should restore Celebração from type=celebration', async () => {
      await setup('/eventos?period=all&type=celebration');

      expect(component.typeFilter()).toBe('celebration');
      expect(ids()).toEqual([2, 4]);
    });

    it('should restore the search term trimmed', async () => {
      await setup('/eventos?search=%20%20Vespertina%20%20');

      expect(component.searchTerm()).toBe('Vespertina');
    });

    it('should restore combined filters from the URL', async () => {
      await setup('/eventos?period=all&search=Missa%20de&type=mass');

      expect(component.periodFilter()).toBe('all');
      expect(component.searchTerm()).toBe('Missa de');
      expect(component.typeFilter()).toBe('mass');
      expect(ids()).toEqual([1, 5]);
    });

    it('should not trigger additional HTTP requests when restoring filters from the URL', async () => {
      await setup('/eventos?period=past&search=missa&type=mass');

      expect(eventService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should keep filters working normally after being restored from the URL', async () => {
      await setup('/eventos?period=past');

      expect(ids()).toEqual([2, 1]);

      selectPeriod('all');

      expect(component.periodFilter()).toBe('all');
      expect(ids()).toEqual([1, 2, 7, 3, 4, 5, 6]);
    });
  });

  describe('query parameter synchronization', () => {
    it('should update the URL when the period filter changes', async () => {
      await setup('/eventos');
      navigateSpy.calls.reset();

      selectPeriod('past');

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      const options = navigateSpy.calls.mostRecent().args[1];
      expect(options.queryParams).toEqual({ period: 'past', search: null, type: null });
    });

    it('should update the URL when the type filter changes', async () => {
      await setup('/eventos');
      navigateSpy.calls.reset();

      setType('mass');

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      const options = navigateSpy.calls.mostRecent().args[1];
      expect(options.queryParams).toEqual({ period: null, search: null, type: 'mass' });
    });

    it('should update the URL when the search term changes', async () => {
      await setup('/eventos');
      navigateSpy.calls.reset();

      setSearch('Missa');

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      const options = navigateSpy.calls.mostRecent().args[1];
      expect(options.queryParams).toEqual({ period: null, search: 'Missa', type: null });
    });

    it('should use replaceUrl instead of adding a new history entry for filter changes', async () => {
      await setup('/eventos');
      navigateSpy.calls.reset();

      selectPeriod('past');

      expect(navigateSpy.calls.mostRecent().args[1].replaceUrl).toBeTrue();
    });

    it('should omit default filter values from the resulting URL', async () => {
      await setup('/eventos?period=past');

      selectPeriod('upcoming');
      await harness.fixture.whenStable();

      expect(location.path()).toBe('/eventos');
    });

    it('should remove all three filter parameters when clearing filters', async () => {
      await setup('/eventos?period=past&search=missa&type=mass');
      navigateSpy.calls.reset();

      component.clearFilters();
      await harness.fixture.whenStable();

      const options = navigateSpy.calls.mostRecent().args[1];
      expect(options.queryParams).toEqual({ period: null, search: null, type: null });
      expect(location.path()).toBe('/eventos');
    });

    it('should not call findAll again when clearing filters', async () => {
      await setup('/eventos?period=past&search=missa&type=mass');

      component.clearFilters();

      expect(eventService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should preserve unknown query parameters when syncing filters', async () => {
      await setup('/eventos?foo=bar');
      navigateSpy.calls.reset();

      selectPeriod('past');
      await harness.fixture.whenStable();

      expect(location.path()).toContain('foo=bar');
      expect(location.path()).toContain('period=past');
    });

    it('should URL-encode spaces and accents in the search parameter', async () => {
      await setup('/eventos');

      setSearch('  Ação de Graças  ');
      await harness.fixture.whenStable();

      expect(location.path()).toBe(`/eventos?search=${encodeURIComponent('Ação de Graças')}`);
    });

    it('should not repeat navigation when the URL already represents the current filter state', async () => {
      await setup('/eventos');
      navigateSpy.calls.reset();

      selectPeriod('past');
      await harness.fixture.whenStable();

      expect(navigateSpy).toHaveBeenCalledTimes(1);

      navigateSpy.calls.reset();
      component.setPeriodFilter('past');
      await harness.fixture.whenStable();

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('invalid query parameters', () => {
    it('should fall back to the default period and strip an invalid period value from the URL', async () => {
      await setup('/eventos?period=future');
      await harness.fixture.whenStable();

      expect(component.periodFilter()).toBe('upcoming');
      expect(location.path()).toBe('/eventos');
    });

    it('should fall back to the default type and strip an invalid type value from the URL', async () => {
      await setup('/eventos?type=church');
      await harness.fixture.whenStable();

      expect(component.typeFilter()).toBe('all');
      expect(location.path()).toBe('/eventos');
    });

    it('should treat blank period and type values as invalid and fall back to defaults', async () => {
      await setup('/eventos?period=&type=');
      await harness.fixture.whenStable();

      expect(component.periodFilter()).toBe('upcoming');
      expect(component.typeFilter()).toBe('all');
      expect(location.path()).toBe('/eventos');
    });

    it('should keep a valid search parameter when another parameter is invalid', async () => {
      await setup('/eventos?period=future&search=Vespertina');
      await harness.fixture.whenStable();

      expect(component.periodFilter()).toBe('upcoming');
      expect(component.searchTerm()).toBe('Vespertina');
      expect(location.path()).toBe('/eventos?search=Vespertina');
    });

    it('should not show an error to the user and should still load events for invalid query parameters', async () => {
      await setup('/eventos?period=future&type=church');

      expect(component.errorMessage()).toBeNull();
      expect(eventService.findAll).toHaveBeenCalledTimes(1);
    });
  });

  function ids(): number[] {
    return component.visibleEvents().map((event) => event.id);
  }

  function selectPeriod(value: 'upcoming' | 'past' | 'all'): void {
    const radio = harness.routeNativeElement?.querySelector(
      `input[name="event-period"][value="${value}"]`,
    ) as HTMLInputElement;
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  function setSearch(value: string): void {
    const input = harness.routeNativeElement?.querySelector('#event-search') as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function setType(value: 'all' | 'mass' | 'celebration'): void {
    const select = harness.routeNativeElement?.querySelector('#event-type') as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  function emptyStateClearButton(): HTMLButtonElement | null {
    return harness.routeNativeElement?.querySelector('.events__feedback .events__button--secondary') ?? null;
  }

  function retryButtonEl(): HTMLButtonElement {
    return harness.routeNativeElement?.querySelector(
      '.events__feedback--error .events__button',
    ) as HTMLButtonElement;
  }

  function textContent(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }
});

describe('EventListComponent detail link navigation', () => {
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventService: jasmine.SpyObj<EventService>;

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(NOW);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    TestBed.resetTestingModule();
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

  it('should preserve the current filters in the public detail link', async () => {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(false);
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

    const harness = await RouterTestingHarness.create('/eventos?type=mass');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/eventos/1?type=mass');
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

  it('should preserve the current filters in the authenticated detail link', async () => {
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

    const harness = await RouterTestingHarness.create('/app/eventos?search=domingo');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/app/eventos/1?search=domingo');
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
