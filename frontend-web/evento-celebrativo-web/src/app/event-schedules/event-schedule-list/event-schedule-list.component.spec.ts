import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import {
  EventSchedulePage,
  EventScheduleQuery,
  EventScheduleResponse,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleListComponent } from './event-schedule-list.component';

describe('EventScheduleListComponent', () => {
  let harness: RouterTestingHarness;
  let component: EventScheduleListComponent;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;
  let router: Router;
  let location: Location;
  let navigateSpy: jasmine.Spy;

  async function setup(
    url = '/escalas',
    response: EventSchedulePage = createPage(),
    isAdmin = false,
  ): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'findMonthlySchedules',
    ]);
    eventScheduleService.findMonthlySchedules.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'escalas', component: EventScheduleListComponent }]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventScheduleService, useValue: eventScheduleService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    navigateSpy = spyOn(router, 'navigate').and.callThrough();

    harness = await RouterTestingHarness.create(url);
    component = harness.routeDebugElement?.componentInstance as EventScheduleListComponent;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(component).toBeTruthy();
  });

  it('should load reader schedules for the current local month on initialization', async () => {
    await setup();

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      type: 'READER',
      page: 0,
      size: 10,
      includeUnassigned: false,
    });
  });

  it('should render returned schedules and participants', async () => {
    await setup();

    const text = textContent();

    expect(text).toContain('Consulta mensal de escalas');
    expect(text).toContain('Missa Dominical');
    expect(text).toContain('Igreja Matriz');
    expect(text).toContain('20/07/2026');
    expect(text).toContain('19:00');
    expect(text).toContain('Maria da Silva');
    expect(text).toContain('Leitores');
  });

  it('should render the event with schedule creation action for administrators', async () => {
    await setup('/escalas', createPage(), true);

    const links = Array.from(
      harness.routeNativeElement?.querySelectorAll('.page-action') ?? [],
    ) as HTMLAnchorElement[];
    const createAction = links.find((link) => link.textContent?.includes('Novo evento com escala'));

    expect(authSessionService.hasAuthority).toHaveBeenCalledWith('ROLE_ADMIN');
    expect(createAction).toBeDefined();
    expect(createAction?.getAttribute('href')).toContain('/app/admin/escalas/novo-evento');
  });

  it('should not render the event assignment audit action for administrators', async () => {
    await setup('/escalas', createPage(), true);

    expect(textContent()).not.toContain('Auditoria de consistência');
  });

  it('should not render the event with schedule creation action for operators', async () => {
    await setup('/escalas', createPage(), false);

    expect(textContent()).not.toContain('Novo evento com escala');
    expect(textContent()).not.toContain('Auditoria de consistência');
  });

  it('should render a link to the full schedule detail without showing ids as text', async () => {
    await setup();

    const detailLink = harness.routeNativeElement?.querySelector(
      '.schedule-card__detail-link',
    ) as HTMLAnchorElement;
    const text = textContent();

    expect(detailLink).not.toBeNull();
    expect(detailLink.textContent).toContain('Ver escala completa');
    expect(detailLink.getAttribute('href')).toContain('/app/escalas/eventos/10');
    expect(detailLink.getAttribute('href')).toContain('type=READER');
    expect(detailLink.getAttribute('href')).toContain('includeUnassigned=false');
    expect(detailLink.getAttribute('href')).toContain('page=0');
    expect(text).not.toContain('eventId');
    expect(text).not.toContain('personId');
    expect(text).not.toContain('locationId');
  });

  it('should forward only the four known parameters to the detail link, even when unknown params are present', async () => {
    await setup('/escalas?foo=bar');

    const detailLink = harness.routeNativeElement?.querySelector(
      '.schedule-card__detail-link',
    ) as HTMLAnchorElement;
    const href = detailLink.getAttribute('href') ?? '';

    expect(href).toContain('/app/escalas/eventos/10');
    expect(href).toContain('type=READER');
    expect(href).toContain('includeUnassigned=false');
    expect(href).toContain('page=0');
    expect(href).not.toContain('foo');
  });

  it('should render a message when an event has no assignments', async () => {
    await setup(
      '/escalas',
      createPage({
        content: [createSchedule({ assignments: [] })],
      }),
    );

    expect(textContent()).toContain('Nenhuma pessoa foi escalada para esta funcao');
  });

  it('should render the empty state when the API returns no schedules', async () => {
    await setup(
      '/escalas',
      createPage({ content: [], totalElements: 0, numberOfElements: 0, empty: true }),
    );

    expect(textContent()).toContain('Nenhuma escala de Leitores foi encontrada');
  });

  it('should render a loading state while the request is pending', async () => {
    const pendingRequest = new Subject<EventSchedulePage>();
    await setup();
    eventScheduleService.findMonthlySchedules.and.returnValue(pendingRequest);

    component.onSubmit();
    harness.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando escalas...');

    pendingRequest.next(createPage());
    pendingRequest.complete();
  });

  it('should show an error message and allow retrying the last valid query', async () => {
    await setup();
    eventScheduleService.findMonthlySchedules.and.returnValues(
      throwError(() => new Error('Failed to load schedules')),
      of(createPage()),
    );
    eventScheduleService.findMonthlySchedules.calls.reset();

    component.onSubmit();
    harness.detectChanges();

    expect(textContent()).toContain('carregar as escalas');

    const retryButton = harness.routeNativeElement?.querySelector(
      '.event-schedules__button',
    ) as HTMLButtonElement;
    retryButton.click();
    harness.detectChanges();

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Missa Dominical');
  });

  it('should show a permission message when the API returns forbidden', async () => {
    await setup();
    eventScheduleService.findMonthlySchedules.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            statusText: 'Forbidden',
          }),
      ),
    );

    component.onSubmit();
    harness.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para consultar as escalas');
  });

  it('should not request the API when the selected month is invalid', async () => {
    await setup();
    eventScheduleService.findMonthlySchedules.calls.reset();

    component.selectedMonth = '2026';
    component.onSubmit();
    harness.detectChanges();

    expect(eventScheduleService.findMonthlySchedules).not.toHaveBeenCalled();
    expect(textContent()).toContain('mes valido');
  });

  it('should not navigate when the selected month is invalid', async () => {
    await setup();
    navigateSpy.calls.reset();

    component.selectedMonth = '2026';
    component.onSubmit();

    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('should request page zero with selected filters when submitting a valid month', async () => {
    await setup();
    eventScheduleService.findMonthlySchedules.calls.reset();

    component.selectedMonth = '2026-08';
    component.selectedType = 'PRIEST';
    component.includeUnassigned = true;
    component.onSubmit();

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: '2026-08-01',
      endDate: '2026-08-31',
      type: 'PRIEST',
      page: 0,
      size: 10,
      includeUnassigned: true,
    });
  });

  it('should restore valid filters from query params when returning from detail', async () => {
    await setup('/escalas?type=PRIEST&month=2026-08&includeUnassigned=true&page=2');

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: '2026-08-01',
      endDate: '2026-08-31',
      type: 'PRIEST',
      page: 2,
      size: 10,
      includeUnassigned: true,
    });
    expect(component.selectedType).toBe('PRIEST');
    expect(component.selectedMonth).toBe('2026-08');
    expect(component.includeUnassigned).toBeTrue();
  });

  it('should restore includeUnassigned=false explicitly and not navigate when the URL is already fully correct', async () => {
    await setup(`/escalas?type=READER&month=${currentYearMonth()}&includeUnassigned=false&page=0`);
    await harness.fixture.whenStable();

    expect(component.includeUnassigned).toBeFalse();
    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('should use defaults when query params are invalid', async () => {
    await setup('/escalas?type=INVALID&month=2026&includeUnassigned=maybe&page=-1');

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      type: 'READER',
      page: 0,
      size: 10,
      includeUnassigned: false,
    });
  });

  it('should reject month 13 and fall back to the current month', async () => {
    await setup('/escalas?month=2026-13');

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({
        startDate: firstDayOfCurrentMonth(),
        endDate: lastDayOfCurrentMonth(),
      }),
    );
  });

  it('should reject a decimal page and fall back to zero', async () => {
    await setup('/escalas?page=1.5');

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ page: 0 }),
    );
  });

  it('should reject a textual page and fall back to zero', async () => {
    await setup('/escalas?page=abc');

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ page: 0 }),
    );
  });

  it('should navigate to the previous and next month from the selected month', async () => {
    await setup();
    eventScheduleService.findMonthlySchedules.calls.reset();

    component.selectedMonth = '2026-07';
    component.previousMonth();
    component.nextMonth();

    expect(eventScheduleService.findMonthlySchedules.calls.argsFor(0)[0]).toEqual({
      startDate: '2026-06-01',
      endDate: '2026-06-30',
      type: 'READER',
      page: 0,
      size: 10,
      includeUnassigned: false,
    });
    expect(eventScheduleService.findMonthlySchedules.calls.argsFor(1)[0]).toEqual({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      type: 'READER',
      page: 0,
      size: 10,
      includeUnassigned: false,
    });
  });

  it('should request the next page while preserving filters', async () => {
    await setup('/escalas', createPage({ totalPages: 2, first: true, last: false }));
    eventScheduleService.findMonthlySchedules.calls.reset();
    eventScheduleService.findMonthlySchedules.and.returnValue(
      of(createPage({ number: 1, totalPages: 2, first: false, last: true })),
    );

    component.nextPage();

    const query = eventScheduleService.findMonthlySchedules.calls.mostRecent()
      .args[0] as EventScheduleQuery;

    expect(query).toEqual({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      type: 'READER',
      page: 1,
      size: 10,
      includeUnassigned: false,
    });
  });

  it('should disable pagination controls at the page limits', async () => {
    await setup('/escalas', createPage({ totalPages: 1, first: true, last: true }));

    const buttons = Array.from(
      harness.routeNativeElement?.querySelectorAll('.schedule-pagination button') ?? [],
    ) as HTMLButtonElement[];

    expect(buttons[0].disabled).toBeTrue();
    expect(buttons[1].disabled).toBeTrue();
  });

  describe('URL synchronization', () => {
    it('should update the URL with the four explicit parameters when none are present', async () => {
      await setup('/escalas');
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('/escalas?');
      expect(path).toContain('type=READER');
      expect(path).toContain(`month=${currentYearMonth()}`);
      expect(path).toContain('includeUnassigned=false');
      expect(path).toContain('page=0');
    });

    it('should update the URL on submit', async () => {
      await setup();
      navigateSpy.calls.reset();

      component.selectedMonth = '2026-08';
      component.selectedType = 'PRIEST';
      component.includeUnassigned = true;
      component.onSubmit();
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('type=PRIEST');
      expect(path).toContain('month=2026-08');
      expect(path).toContain('includeUnassigned=true');
      expect(path).toContain('page=0');
    });

    it('should use replaceUrl when submitting', async () => {
      await setup();
      navigateSpy.calls.reset();

      component.selectedMonth = '2026-08';
      component.selectedType = 'PRIEST';
      component.onSubmit();

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      expect(navigateSpy.calls.mostRecent().args[1].replaceUrl).toBeTrue();
      expect(navigateSpy.calls.mostRecent().args[1].queryParamsHandling).toBe('merge');
    });

    it('should reset page to zero in the URL when submitting from a non-zero page', async () => {
      await setup('/escalas?page=3', createPage({ number: 3, totalPages: 5 }));
      await harness.fixture.whenStable();
      expect(location.path()).toContain('page=3');

      eventScheduleService.findMonthlySchedules.and.returnValue(of(createPage()));
      navigateSpy.calls.reset();

      component.onSubmit();
      await harness.fixture.whenStable();

      expect(location.path()).toContain('page=0');
    });

    it('should update month and reset page in the URL on previous month', async () => {
      await setup('/escalas?page=2');
      await harness.fixture.whenStable();

      component.previousMonth();
      await harness.fixture.whenStable();

      const expectedMonth = shiftYearMonth(currentYearMonth(), -1);

      expect(location.path()).toContain(`month=${expectedMonth}`);
      expect(location.path()).toContain('page=0');
    });

    it('should update month and reset page in the URL on next month', async () => {
      await setup('/escalas?page=2');
      await harness.fixture.whenStable();

      component.nextMonth();
      await harness.fixture.whenStable();

      const expectedMonth = shiftYearMonth(currentYearMonth(), 1);

      expect(location.path()).toContain(`month=${expectedMonth}`);
      expect(location.path()).toContain('page=0');
    });

    it('should only change page in the URL on previous page', async () => {
      await setup(
        '/escalas?type=PRIEST&month=2026-08&includeUnassigned=true&page=2',
        createPage({ number: 2, totalPages: 5, first: false, last: false }),
      );
      await harness.fixture.whenStable();
      eventScheduleService.findMonthlySchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 5, first: false, last: false })),
      );

      component.previousPage();
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('type=PRIEST');
      expect(path).toContain('month=2026-08');
      expect(path).toContain('includeUnassigned=true');
      expect(path).toContain('page=1');
    });

    it('should only change page in the URL on next page', async () => {
      await setup(
        '/escalas?type=PRIEST&month=2026-08&includeUnassigned=true&page=0',
        createPage({ totalPages: 3, first: true, last: false }),
      );
      await harness.fixture.whenStable();
      eventScheduleService.findMonthlySchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 3, first: false, last: false })),
      );

      component.nextPage();
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('type=PRIEST');
      expect(path).toContain('month=2026-08');
      expect(path).toContain('includeUnassigned=true');
      expect(path).toContain('page=1');
    });

    it('should preserve unknown query parameters when synchronizing the URL', async () => {
      await setup('/escalas?foo=bar');
      await harness.fixture.whenStable();
      navigateSpy.calls.reset();

      component.onSubmit();
      await harness.fixture.whenStable();

      expect(location.path()).toContain('foo=bar');
    });

    it('should correct only the invalid known parameters, preserving unknown ones', async () => {
      await setup('/escalas?type=INVALID&foo=bar');
      await harness.fixture.whenStable();

      expect(location.path()).toContain('foo=bar');
      expect(location.path()).toContain('type=READER');
    });

    it('should not duplicate the request when correcting invalid query params', async () => {
      await setup('/escalas?type=INVALID&month=2026&includeUnassigned=maybe&page=-1');
      await harness.fixture.whenStable();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(1);
    });

    it('should correct the URL page when the backend returns a different page number', async () => {
      await setup('/escalas?page=5', createPage({ number: 0, totalPages: 1 }));
      await harness.fixture.whenStable();

      expect(location.path()).toContain('page=0');
      expect(location.path()).not.toContain('page=5');
    });

    it('should not call the backend again when correcting the page number', async () => {
      await setup('/escalas?page=5', createPage({ number: 0, totalPages: 1 }));
      await harness.fixture.whenStable();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(1);
    });
  });

  describe('retry', () => {
    it('should preserve the last valid filters and page when retrying', async () => {
      await setup(
        '/escalas?type=PRIEST&month=2026-08&includeUnassigned=true',
        createPage({ number: 0, totalPages: 3, first: true, last: false }),
      );

      eventScheduleService.findMonthlySchedules.and.returnValue(
        throwError(() => new Error('Request failed')),
      );

      component.nextPage();
      harness.detectChanges();

      eventScheduleService.findMonthlySchedules.calls.reset();
      eventScheduleService.findMonthlySchedules.and.returnValue(of(createPage()));

      component.selectedType = 'COMMENTATOR';
      component.selectedMonth = '2026-01';
      component.retry();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        type: 'PRIEST',
        page: 1,
        size: 10,
        includeUnassigned: true,
      });
    });

    it('should not navigate again on retry when the URL already matches the last valid query', async () => {
      await setup();
      eventScheduleService.findMonthlySchedules.and.returnValue(
        throwError(() => new Error('Request failed')),
      );

      component.onSubmit();
      harness.detectChanges();
      await harness.fixture.whenStable();

      navigateSpy.calls.reset();
      eventScheduleService.findMonthlySchedules.and.returnValue(of(createPage()));

      component.retry();

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  describe('duplicate call prevention', () => {
    it('should block a duplicate submit while loading', async () => {
      const pendingRequest = new Subject<EventSchedulePage>();
      await setup();
      eventScheduleService.findMonthlySchedules.and.returnValue(pendingRequest);
      eventScheduleService.findMonthlySchedules.calls.reset();

      component.onSubmit();
      component.onSubmit();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(1);

      pendingRequest.next(createPage());
      pendingRequest.complete();
    });

    it('should block duplicate pagination while loading', async () => {
      const pendingRequest = new Subject<EventSchedulePage>();
      await setup('/escalas', createPage({ totalPages: 3, first: true, last: false }));
      eventScheduleService.findMonthlySchedules.and.returnValue(pendingRequest);
      eventScheduleService.findMonthlySchedules.calls.reset();

      component.nextPage();
      component.nextPage();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(1);

      pendingRequest.next(createPage({ number: 1, totalPages: 3 }));
      pendingRequest.complete();
    });

    it('should block a duplicate retry while loading', async () => {
      await setup();
      eventScheduleService.findMonthlySchedules.and.returnValue(
        throwError(() => new Error('Request failed')),
      );

      component.onSubmit();
      harness.detectChanges();

      const pendingRetry = new Subject<EventSchedulePage>();
      eventScheduleService.findMonthlySchedules.and.returnValue(pendingRetry);
      eventScheduleService.findMonthlySchedules.calls.reset();

      component.retry();
      component.retry();

      expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledTimes(1);

      pendingRetry.next(createPage());
      pendingRetry.complete();
    });
  });

  function textContent(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }
});

function createSchedule(overrides: Partial<EventScheduleResponse> = {}): EventScheduleResponse {
  return {
    eventId: 10,
    eventName: 'Missa Dominical',
    eventDate: '2026-07-20',
    eventTime: '19:00:00',
    massOrCelebration: true,
    locationId: 2,
    churchName: 'Igreja Matriz',
    assignmentType: 'READER',
    assignments: [{ personId: 15, personName: 'Maria da Silva' }],
    ...overrides,
  };
}

function createPage(overrides: Partial<EventSchedulePage> = {}): EventSchedulePage {
  return {
    content: [createSchedule()],
    totalPages: 1,
    totalElements: 1,
    first: true,
    last: true,
    size: 10,
    number: 0,
    numberOfElements: 1,
    empty: false,
    ...overrides,
  };
}

function firstDayOfCurrentMonth(): string {
  const today = new Date();

  return formatLocalDate(new Date(today.getFullYear(), today.getMonth(), 1));
}

function lastDayOfCurrentMonth(): string {
  const today = new Date();

  return formatLocalDate(new Date(today.getFullYear(), today.getMonth() + 1, 0));
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function currentYearMonth(): string {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');

  return `${year}-${month}`;
}

function shiftYearMonth(yearMonth: string, offset: number): string {
  const [year, month] = yearMonth.split('-').map(Number);
  const shifted = new Date(year, month - 1 + offset, 1);

  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`;
}
