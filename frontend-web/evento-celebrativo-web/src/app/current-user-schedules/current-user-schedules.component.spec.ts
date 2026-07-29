import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import {
  CurrentUserSchedule,
  CurrentUserSchedulePage,
  ScheduleParticipationResponse,
} from './current-user-schedule.models';
import { CurrentUserScheduleService } from './current-user-schedule.service';
import { CurrentUserSchedulesComponent } from './current-user-schedules.component';

const NOW = new Date(2026, 6, 20, 10, 0, 0);
const DEFAULT_START = '2026-07-01';
const DEFAULT_END = '2026-07-31';

describe('CurrentUserSchedulesComponent', () => {
  let harness: RouterTestingHarness;
  let component: CurrentUserSchedulesComponent;
  let currentUserScheduleService: jasmine.SpyObj<CurrentUserScheduleService>;
  let router: Router;
  let location: Location;
  let navigateSpy: jasmine.Spy;

  async function setup(
    url = '/minhas-escalas',
    response: CurrentUserSchedulePage = createPage(),
  ): Promise<void> {
    currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
      'CurrentUserScheduleService',
      ['findSchedules', 'updateParticipation'],
    );
    currentUserScheduleService.findSchedules.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'minhas-escalas', component: CurrentUserSchedulesComponent }]),
        { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    navigateSpy = spyOn(router, 'navigate').and.callThrough();

    harness = await RouterTestingHarness.create(url);
    component = harness.routeDebugElement?.componentInstance as CurrentUserSchedulesComponent;
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

  it('should default to the first and last day of the current local month when no query params are present', async () => {
    await setup();

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
      startDate: DEFAULT_START,
      endDate: DEFAULT_END,
      page: 0,
      size: 10,
    });
  });

  it('should correct the URL when no query params are present', async () => {
    await setup();
    await harness.fixture.whenStable();

    const path = location.path();

    expect(path).toContain('/minhas-escalas?');
    expect(path).toContain(`startDate=${DEFAULT_START}`);
    expect(path).toContain(`endDate=${DEFAULT_END}`);
    expect(path).toContain('page=0');
  });

  it('should restore a valid startDate from the query params', async () => {
    await setup('/minhas-escalas?startDate=2026-06-01&endDate=2026-06-30&page=0');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ startDate: '2026-06-01' }),
    );
  });

  it('should restore a valid endDate from the query params', async () => {
    await setup('/minhas-escalas?startDate=2026-06-01&endDate=2026-06-30&page=0');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ endDate: '2026-06-30' }),
    );
  });

  it('should restore a valid page from the query params', async () => {
    await setup(
      '/minhas-escalas?startDate=2026-07-01&endDate=2026-07-31&page=2',
      createPage({ number: 2, totalPages: 5 }),
    );

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ page: 2 }),
    );
  });

  it('should execute a single request on initialization', async () => {
    await setup();

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
  });

  it('should always request a fixed size of 10, never present in the URL', async () => {
    await setup();
    await harness.fixture.whenStable();

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ size: 10 }),
    );
    expect(location.path()).not.toContain('size=');
  });

  it('should reject a non-existent date and fall back to the current month', async () => {
    await setup('/minhas-escalas?startDate=2026-02-30&endDate=2026-07-31');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ startDate: DEFAULT_START, endDate: DEFAULT_END }),
    );
  });

  describe('local date validation (timezone-sensitive)', () => {
    it('should accept 2024-02-29 as a valid leap year date', async () => {
      await setup('/minhas-escalas?startDate=2024-02-29&endDate=2024-02-29');

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ startDate: '2024-02-29', endDate: '2024-02-29' }),
      );
    });

    it('should reject 2025-02-29 (2025 is not a leap year) and fall back to the current month', async () => {
      await setup('/minhas-escalas?startDate=2025-02-29&endDate=2025-02-29');

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ startDate: DEFAULT_START, endDate: DEFAULT_END }),
      );
    });

    it('should reject 2026-04-31 (April has only 30 days) and fall back to the current month', async () => {
      await setup('/minhas-escalas?startDate=2026-04-31&endDate=2026-04-31');

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ startDate: DEFAULT_START, endDate: DEFAULT_END }),
      );
    });

    it('should accept 2026-12-31 as a valid end-of-year date', async () => {
      await setup('/minhas-escalas?startDate=2026-12-01&endDate=2026-12-31');

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ startDate: '2026-12-01', endDate: '2026-12-31' }),
      );
    });
  });

  it('should reject a negative page and fall back to zero', async () => {
    await setup('/minhas-escalas?page=-1');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ page: 0 }),
    );
  });

  it('should reject a decimal page and fall back to zero', async () => {
    await setup('/minhas-escalas?page=1.5');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ page: 0 }),
    );
  });

  it('should fall back to the default period when startDate is after endDate', async () => {
    await setup('/minhas-escalas?startDate=2026-07-31&endDate=2026-07-01');

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ startDate: DEFAULT_START, endDate: DEFAULT_END }),
    );
  });

  it('should not duplicate the request when correcting invalid query params', async () => {
    await setup('/minhas-escalas?startDate=invalid&page=-1');
    await harness.fixture.whenStable();

    expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
  });

  it('should preserve unknown query parameters when synchronizing the URL', async () => {
    await setup('/minhas-escalas?foo=bar');
    await harness.fixture.whenStable();

    expect(location.path()).toContain('foo=bar');
  });

  describe('submit', () => {
    it('should reset the page to zero on submit', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-07-01&endDate=2026-07-31&page=2',
        createPage({ number: 2, totalPages: 5 }),
      );
      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(of(createPage()));

      component.form.setValue({ startDate: '2026-08-01', endDate: '2026-08-31' });
      component.submit();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 0,
        size: 10,
      });
    });

    it('should update the URL on submit', async () => {
      await setup();
      navigateSpy.calls.reset();

      component.form.setValue({ startDate: '2026-08-01', endDate: '2026-08-31' });
      component.submit();
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('startDate=2026-08-01');
      expect(path).toContain('endDate=2026-08-31');
      expect(path).toContain('page=0');
    });

    it('should use replaceUrl and merge query params handling on submit', async () => {
      await setup();
      navigateSpy.calls.reset();

      component.form.setValue({ startDate: '2026-08-01', endDate: '2026-08-31' });
      component.submit();

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      expect(navigateSpy.calls.mostRecent().args[1].replaceUrl).toBeTrue();
      expect(navigateSpy.calls.mostRecent().args[1].queryParamsHandling).toBe('merge');
    });

    it('should execute a single request on submit', async () => {
      await setup();
      currentUserScheduleService.findSchedules.calls.reset();

      component.form.setValue({ startDate: '2026-08-01', endDate: '2026-08-31' });
      component.submit();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
    });

    it('should not navigate when the form is invalid', async () => {
      await setup();
      navigateSpy.calls.reset();

      component.form.setValue({ startDate: '', endDate: '2026-07-31' });
      component.submit();

      expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not call the service when the form is invalid', async () => {
      await setup();
      currentUserScheduleService.findSchedules.calls.reset();

      component.form.setValue({ startDate: '', endDate: '2026-07-31' });
      component.submit();
      harness.detectChanges();

      expect(currentUserScheduleService.findSchedules).not.toHaveBeenCalled();
      expect(textContent()).toContain('Informe a data inicial.');
    });

    it('should show a validation message for an inverted date range and not call the service', async () => {
      await setup();
      currentUserScheduleService.findSchedules.calls.reset();

      component.form.setValue({ startDate: '2026-07-31', endDate: '2026-07-01' });
      component.submit();
      harness.detectChanges();

      expect(currentUserScheduleService.findSchedules).not.toHaveBeenCalled();
      expect(textContent()).toContain('A data inicial não pode ser posterior à data final.');
    });

    it('should block a duplicate submit while loading', async () => {
      const pendingRequest = new Subject<CurrentUserSchedulePage>();
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(pendingRequest);
      currentUserScheduleService.findSchedules.calls.reset();

      component.submit();
      component.submit();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);

      pendingRequest.next(createPage());
      pendingRequest.complete();
    });
  });

  describe('loading, empty and error states', () => {
    it('should show a loading state while the request is pending', async () => {
      const pendingRequest = new Subject<CurrentUserSchedulePage>();
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(pendingRequest);

      component.submit();
      harness.detectChanges();

      expect(component.isLoading()).toBeTrue();
      expect(textContent()).toContain('Carregando suas escalas...');

      pendingRequest.next(createPage());
      pendingRequest.complete();
    });

    it('should show the empty state message without treating it as an error', async () => {
      await setup(
        '/minhas-escalas',
        createPage({ content: [], totalElements: 0, totalPages: 0, numberOfElements: 0, empty: true }),
      );

      expect(textContent()).toContain('Nenhuma escala foi encontrada para o período informado.');
      expect(component.errorMessage()).toBeNull();
    });

    it('should show a specific message for HTTP 400', async () => {
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 400 })),
      );

      component.submit();
      harness.detectChanges();

      expect(textContent()).toContain(
        'O período informado é inválido. Revise as datas e tente novamente.',
      );
    });

    it('should show a specific message for HTTP 403', async () => {
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      );

      component.submit();
      harness.detectChanges();

      expect(textContent()).toContain('Não foi possível acessar suas escalas.');
    });

    it('should show a specific message for HTTP 404', async () => {
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 404 })),
      );

      component.submit();
      harness.detectChanges();

      expect(textContent()).toContain(
        'Não foi possível localizar a pessoa associada à sua conta autenticada.',
      );
    });

    it('should show a generic message for unexpected errors', async () => {
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      component.submit();
      harness.detectChanges();

      expect(textContent()).toContain('Não foi possível carregar suas escalas. Tente novamente.');
    });
  });

  describe('retry', () => {
    it('should preserve the last valid query and not rebuild it from the form', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=0',
        createPage({ totalPages: 3, first: true, last: false }),
      );
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      component.nextPage();
      harness.detectChanges();

      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(of(createPage()));
      component.form.setValue({ startDate: '2026-01-01', endDate: '2026-01-31' });

      component.retry();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      });
    });

    it('should block a duplicate retry while loading', async () => {
      await setup();
      currentUserScheduleService.findSchedules.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      component.submit();
      harness.detectChanges();

      const pendingRetry = new Subject<CurrentUserSchedulePage>();
      currentUserScheduleService.findSchedules.and.returnValue(pendingRetry);
      currentUserScheduleService.findSchedules.calls.reset();

      component.retry();
      component.retry();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);

      pendingRetry.next(createPage());
      pendingRetry.complete();
    });
  });

  describe('pagination', () => {
    it('should request the previous page while preserving filters', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=2',
        createPage({ number: 2, totalPages: 5, first: false, last: false }),
      );
      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 5, first: false, last: false })),
      );

      component.previousPage();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      });
    });

    it('should request the next page while preserving filters', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=0',
        createPage({ number: 0, totalPages: 2, first: true, last: false }),
      );
      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 2, first: false, last: true })),
      );

      component.nextPage();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      });
    });

    it('should preserve startDate and endDate in the URL when paginating', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=0',
        createPage({ number: 0, totalPages: 2, first: true, last: false }),
      );
      await harness.fixture.whenStable();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 2, first: false, last: true })),
      );

      component.nextPage();
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('startDate=2026-08-01');
      expect(path).toContain('endDate=2026-08-31');
      expect(path).toContain('page=1');
    });

    it('should not go beyond the first page', async () => {
      await setup('/minhas-escalas', createPage({ number: 0, totalPages: 3, first: true }));
      currentUserScheduleService.findSchedules.calls.reset();

      component.previousPage();

      expect(currentUserScheduleService.findSchedules).not.toHaveBeenCalled();
    });

    it('should block duplicate pagination while loading', async () => {
      const pendingRequest = new Subject<CurrentUserSchedulePage>();
      await setup('/minhas-escalas', createPage({ totalPages: 3, first: true, last: false }));
      currentUserScheduleService.findSchedules.and.returnValue(pendingRequest);
      currentUserScheduleService.findSchedules.calls.reset();

      component.nextPage();
      component.nextPage();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);

      pendingRequest.next(createPage({ number: 1, totalPages: 3 }));
      pendingRequest.complete();
    });
  });

  describe('page beyond the valid limit', () => {
    it('should correct to the last valid page when the requested page is empty beyond the limit', async () => {
      currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
        'CurrentUserScheduleService',
        ['findSchedules', 'updateParticipation'],
      );
      currentUserScheduleService.findSchedules.and.returnValues(
        of(createPage({ content: [], totalElements: 12, totalPages: 2, number: 5, empty: true })),
        of(createPage({ number: 1, totalPages: 2, totalElements: 12, first: false, last: true })),
      );

      await TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'minhas-escalas', component: CurrentUserSchedulesComponent }]),
          { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
        ],
      }).compileComponents();

      harness = await RouterTestingHarness.create(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=5',
      );
      component = harness.routeDebugElement?.componentInstance as CurrentUserSchedulesComponent;
      location = TestBed.inject(Location);
      await harness.fixture.whenStable();

      expect(component.currentPage()).toBe(1);
      expect(location.path()).toContain('page=1');
    });

    it('should execute only the single necessary follow-up request when correcting the page', async () => {
      currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
        'CurrentUserScheduleService',
        ['findSchedules', 'updateParticipation'],
      );
      currentUserScheduleService.findSchedules.and.returnValues(
        of(createPage({ content: [], totalElements: 12, totalPages: 2, number: 5, empty: true })),
        of(createPage({ number: 1, totalPages: 2, totalElements: 12, first: false, last: true })),
      );

      await TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'minhas-escalas', component: CurrentUserSchedulesComponent }]),
          { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
        ],
      }).compileComponents();

      harness = await RouterTestingHarness.create(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=5',
      );
      await harness.fixture.whenStable();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(2);
      expect(currentUserScheduleService.findSchedules.calls.argsFor(1)[0]).toEqual({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      });
    });

    it('should correct the URL when the backend normalizes page.number without needing a new request', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=3',
        createPage({ number: 0, totalPages: 1, first: true, last: true }),
      );
      await harness.fixture.whenStable();

      expect(location.path()).toContain('page=0');
      expect(location.path()).not.toContain('page=3');
      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
    });

    it('should update lastValidQuery after page.number normalization, so retry reuses the normalized page', async () => {
      await setup(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=5',
        createPage({ number: 2, totalPages: 3, first: false, last: false, empty: false }),
      );
      await harness.fixture.whenStable();

      expect(component.currentPage()).toBe(2);
      expect(location.path()).toContain('page=2');
      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);

      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 2, totalPages: 3 })),
      );
      navigateSpy.calls.reset();

      component.retry();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 2,
        size: 10,
      });
      expect(location.path()).toContain('page=2');
      expect(location.path()).not.toContain('page=5');
    });

    it('should reuse the corrected page on a subsequent retry after an empty page beyond the limit', async () => {
      currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
        'CurrentUserScheduleService',
        ['findSchedules', 'updateParticipation'],
      );
      currentUserScheduleService.findSchedules.and.returnValues(
        of(createPage({ content: [], totalElements: 12, totalPages: 2, number: 5, empty: true })),
        of(createPage({ number: 1, totalPages: 2, totalElements: 12, first: false, last: true })),
      );

      await TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'minhas-escalas', component: CurrentUserSchedulesComponent }]),
          { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
        ],
      }).compileComponents();

      harness = await RouterTestingHarness.create(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=5',
      );
      component = harness.routeDebugElement?.componentInstance as CurrentUserSchedulesComponent;
      await harness.fixture.whenStable();

      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 2, totalElements: 12 })),
      );

      component.retry();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith({
        startDate: '2026-08-01',
        endDate: '2026-08-31',
        page: 1,
        size: 10,
      });
    });

    it('should keep isLoading true and block pagination, submit and retry until the corrective request finishes, then allow retry on its error', async () => {
      const correctivePending = new Subject<CurrentUserSchedulePage>();
      currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
        'CurrentUserScheduleService',
        ['findSchedules', 'updateParticipation'],
      );
      currentUserScheduleService.findSchedules.and.returnValues(
        of(createPage({ content: [], totalElements: 12, totalPages: 2, number: 5, empty: true })),
        correctivePending,
      );

      await TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'minhas-escalas', component: CurrentUserSchedulesComponent }]),
          { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
        ],
      }).compileComponents();

      harness = await RouterTestingHarness.create(
        '/minhas-escalas?startDate=2026-08-01&endDate=2026-08-31&page=5',
      );
      component = harness.routeDebugElement?.componentInstance as CurrentUserSchedulesComponent;
      await harness.fixture.whenStable();

      expect(component.isLoading()).toBeTrue();
      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(2);

      component.retry();
      component.nextPage();
      component.previousPage();
      component.submit();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(2);

      correctivePending.error(new HttpErrorResponse({ status: 500 }));

      expect(component.isLoading()).toBeFalse();

      currentUserScheduleService.findSchedules.calls.reset();
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createPage({ number: 1, totalPages: 2, totalElements: 12 })),
      );

      component.retry();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ page: 1 }),
      );
    });
  });

  describe('display', () => {
    it('should render the location name when present', async () => {
      await setup('/minhas-escalas', createPage({ content: [createSchedule({ locationName: 'Igreja Matriz' })] }));

      expect(textContent()).toContain('Igreja Matriz');
    });

    it('should render a fallback message when the location is null', async () => {
      await setup(
        '/minhas-escalas',
        createPage({ content: [createSchedule({ locationId: null, locationName: null })] }),
      );

      expect(textContent()).toContain('Local não informado.');
    });

    it('should label a mass event as Missa', async () => {
      await setup('/minhas-escalas', createPage({ content: [createSchedule({ massOrCelebration: true })] }));

      expect(textContent()).toContain('Missa');
    });

    it('should label a non-mass event as Celebração', async () => {
      await setup('/minhas-escalas', createPage({ content: [createSchedule({ massOrCelebration: false })] }));

      expect(textContent()).toContain('Celebração');
    });

    it('should translate all five assignment types', async () => {
      await setup(
        '/minhas-escalas',
        createPage({
          content: [
            createSchedule({
              eventId: 1,
              assignments: ['PRIEST', 'READER', 'COMMENTATOR', 'MINISTER_OF_THE_WORD', 'EUCHARISTIC_MINISTER'],
            }),
          ],
        }),
      );

      const text = textContent();

      expect(text).toContain('Padre');
      expect(text).toContain('Leitor');
      expect(text).toContain('Comentarista');
      expect(text).toContain('Ministro da Palavra');
      expect(text).toContain('Ministro da Eucaristia');
    });

    it('should render two assignments within the same card', async () => {
      await setup(
        '/minhas-escalas',
        createPage({ content: [createSchedule({ eventId: 7, assignments: ['READER', 'COMMENTATOR'] })] }),
      );

      const card = harness.routeNativeElement?.querySelector(
        '.current-user-schedules__card',
      ) as HTMLElement;

      expect(card.textContent).toContain('Leitor');
      expect(card.textContent).toContain('Comentarista');
    });

    it('should not duplicate an event that has multiple assignments', async () => {
      await setup(
        '/minhas-escalas',
        createPage({
          content: [createSchedule({ eventId: 7, assignments: ['READER', 'COMMENTATOR'] })],
          numberOfElements: 1,
        }),
      );

      const cards = harness.routeNativeElement?.querySelectorAll('.current-user-schedules__card');

      expect(cards?.length).toBe(1);
    });

    it('should not render other participants, ids or technical values', async () => {
      await setup(
        '/minhas-escalas',
        createPage({ content: [createSchedule({ eventId: 99, locationId: 3 })] }),
      );

      const text = textContent();

      expect(text).not.toContain('eventId');
      expect(text).not.toContain('locationId');
      expect(text).not.toContain('personId');
      expect(text).not.toContain('phoneNumber');
      expect(text).not.toContain('ROLE_');
      expect(text).not.toContain('99');
      expect(text).not.toContain('READER');
    });

    it('should expose basic accessibility structure', async () => {
      await setup();

      const nativeElement = harness.routeNativeElement as HTMLElement;

      expect(nativeElement.querySelectorAll('h1').length).toBe(1);
      expect(nativeElement.querySelector('label[for="schedules-start-date"]')).not.toBeNull();
      expect(nativeElement.querySelector('label[for="schedules-end-date"]')).not.toBeNull();
      expect(nativeElement.querySelector('#schedules-start-date')).not.toBeNull();
      expect(nativeElement.querySelector('#schedules-end-date')).not.toBeNull();
      expect(nativeElement.querySelector('nav[aria-label="Paginação das minhas escalas"]')).not.toBeNull();

      const submitButton = Array.from(nativeElement.querySelectorAll('button')).find(
        (button) => button.textContent?.trim() === 'Consultar',
      ) as HTMLButtonElement;

      expect(submitButton.getAttribute('type')).toBe('submit');
    });
  });

  describe('participation', () => {
    function card(): HTMLElement {
      return harness.routeNativeElement?.querySelector(
        '.current-user-schedules__card',
      ) as HTMLElement;
    }

    function findButton(text: string): HTMLButtonElement {
      const button = Array.from(card().querySelectorAll('button')).find(
        (candidate) => candidate.textContent?.trim() === text,
      ) as HTMLButtonElement | undefined;

      if (!button) {
        throw new Error(`Button "${text}" not found`);
      }

      return button;
    }

    function findTextarea(): HTMLTextAreaElement {
      return card().querySelector('textarea') as HTMLTextAreaElement;
    }

    function setTextareaValue(value: string): void {
      const textarea = findTextarea();
      textarea.value = value;
      textarea.dispatchEvent(new Event('input'));
    }

    describe('PENDING state', () => {
      it('should show "Aguardando resposta"', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule()] }));

        expect(card().textContent).toContain('Aguardando resposta');
      });

      it('should show both action buttons', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule()] }));

        expect(() => findButton('Confirmar participação')).not.toThrow();
        expect(() => findButton('Não poderei participar')).not.toThrow();
      });
    });

    describe('CONFIRMED state', () => {
      it('should show "Participação confirmada"', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [createSchedule({ participationStatus: 'CONFIRMED', respondedAt: '2026-07-18T10:00:00' })],
          }),
        );

        expect(card().textContent).toContain('Participação confirmada');
      });

      it('should show the formatted respondedAt', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [createSchedule({ participationStatus: 'CONFIRMED', respondedAt: '2026-07-18T10:05:00' })],
          }),
        );

        expect(card().textContent).toContain('Respondido em: 18/07/2026 10:05');
      });

      it('should not show a decline reason', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'CONFIRMED',
                declineReason: null,
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(card().textContent).not.toContain('Motivo:');
      });
    });

    describe('DECLINED state', () => {
      it('should show "Não participará"', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(card().textContent).toContain('Não participará');
      });

      it('should show the decline reason when present', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(card().textContent).toContain('Motivo: Estarei fora da cidade.');
      });

      it('should not show a reason line when declineReason is null', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'DECLINED',
                declineReason: null,
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(card().textContent).not.toContain('Motivo:');
      });

      it('should show the respondedAt', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(card().textContent).toContain('Respondido em: 18/07/2026 10:00');
      });

      it('should offer confirm and change-decline actions', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        expect(() => findButton('Confirmar participação')).not.toThrow();
        expect(() => findButton('Alterar recusa')).not.toThrow();
      });
    });

    describe('confirming participation', () => {
      it('should call updateParticipation with the correct eventId and body', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED' })),
        );

        findButton('Confirmar participação').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledOnceWith(15, {
          status: 'CONFIRMED',
          declineReason: null,
        });
      });

      it('should update only the corresponding card on success', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [createSchedule({ eventId: 15 }), createSchedule({ eventId: 16 })],
            numberOfElements: 2,
          }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED', respondedAt: '2026-07-19T09:00:00' })),
        );
        harness.detectChanges();

        findButton('Confirmar participação').click();
        harness.detectChanges();

        const cards = harness.routeNativeElement?.querySelectorAll('.current-user-schedules__card');
        expect(cards?.[0].textContent).toContain('Participação confirmada');
        expect(cards?.[1].textContent).toContain('Aguardando resposta');
      });

      it('should clear a previous decline reason after confirming', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                eventId: 15,
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED', declineReason: null })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).not.toContain('Motivo:');
      });

      it('should preserve assignments and not duplicate the event after a successful update', async () => {
        await setup(
          '/minhas-escalas',
          createPage({ content: [createSchedule({ eventId: 15, assignments: ['READER', 'COMMENTATOR'] })] }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED' })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        const cards = harness.routeNativeElement?.querySelectorAll('.current-user-schedules__card');
        expect(cards?.length).toBe(1);
        expect(card().textContent).toContain('Leitor');
        expect(card().textContent).toContain('Comentarista');
      });

      it('should show a success feedback message', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED' })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Participação confirmada com sucesso.');
      });

      it('should block a duplicate confirm submit while pending', async () => {
        const pending = new Subject<ScheduleParticipationResponse>();
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(pending);

        component.confirmParticipation(createSchedule({ eventId: 15 }));
        component.confirmParticipation(createSchedule({ eventId: 15 }));

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledTimes(1);

        pending.next(createParticipationResponse({ eventId: 15 }));
        pending.complete();
      });
    });

    describe('declining participation', () => {
      it('should open the inline form', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));

        findButton('Não poderei participar').click();
        harness.detectChanges();

        expect(findTextarea()).not.toBeNull();
      });

      it('should prefill the textarea when editing an existing decline', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                eventId: 15,
                participationStatus: 'DECLINED',
                declineReason: 'Estarei fora da cidade.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );

        findButton('Alterar recusa').click();
        harness.detectChanges();

        expect(findTextarea().value).toBe('Estarei fora da cidade.');
      });

      it('should not require a reason to submit', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED', declineReason: null })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledOnceWith(15, {
          status: 'DECLINED',
          declineReason: null,
        });
      });

      it('should trim the reason before sending', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED', declineReason: 'Estarei fora.' })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('  Estarei fora.  ');
        harness.detectChanges();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledOnceWith(15, {
          status: 'DECLINED',
          declineReason: 'Estarei fora.',
        });
      });

      it('should turn a blank reason into null', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED', declineReason: null })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('    ');
        harness.detectChanges();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledOnceWith(15, {
          status: 'DECLINED',
          declineReason: null,
        });
      });

      it('should accept exactly 500 characters', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED' })),
        );
        const reason = 'a'.repeat(500);

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue(reason);
        harness.detectChanges();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledOnceWith(15, {
          status: 'DECLINED',
          declineReason: reason,
        });
      });

      it('should block the request beyond 500 characters', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        const reason = 'a'.repeat(501);

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue(reason);
        harness.detectChanges();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).not.toHaveBeenCalled();
      });

      it('should show a character counter', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('abc');
        harness.detectChanges();

        expect(card().textContent).toContain('3/500');
      });

      it('should block a duplicate submit while pending', async () => {
        const pending = new Subject<ScheduleParticipationResponse>();
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(pending);

        findButton('Não poderei participar').click();
        harness.detectChanges();
        findButton('Salvar recusa').click();
        findButton('Salvar recusa').click();

        expect(currentUserScheduleService.updateParticipation).toHaveBeenCalledTimes(1);

        pending.next(createParticipationResponse({ eventId: 15, status: 'DECLINED' }));
        pending.complete();
      });

      it('should keep the cards visible while the PUT is pending', async () => {
        const pending = new Subject<ScheduleParticipationResponse>();
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(pending);

        findButton('Não poderei participar').click();
        harness.detectChanges();
        findButton('Salvar recusa').click();
        harness.detectChanges();

        expect(card()).not.toBeNull();
        expect(textContent()).not.toContain('Carregando suas escalas...');

        pending.next(createParticipationResponse({ eventId: 15, status: 'DECLINED' }));
        pending.complete();
      });

      it('should block filter submit and pagination while a PUT is pending', async () => {
        const pending = new Subject<ScheduleParticipationResponse>();
        await setup(
          '/minhas-escalas',
          createPage({ content: [createSchedule({ eventId: 15 })], totalPages: 2, last: false }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(pending);
        currentUserScheduleService.findSchedules.calls.reset();

        findButton('Não poderei participar').click();
        harness.detectChanges();
        findButton('Salvar recusa').click();

        component.submit();
        component.nextPage();
        component.previousPage();
        component.retry();

        expect(currentUserScheduleService.findSchedules).not.toHaveBeenCalled();

        pending.next(createParticipationResponse({ eventId: 15, status: 'DECLINED' }));
        pending.complete();
      });

      it('should close the form on success', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED' })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        findButton('Salvar recusa').click();
        harness.detectChanges();

        expect(card().querySelector('textarea')).toBeNull();
      });

      it('should preserve the typed text on error', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 500 })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('Estarei fora da cidade.');
        harness.detectChanges();
        findButton('Salvar recusa').click();
        harness.detectChanges();

        expect(findTextarea().value).toBe('Estarei fora da cidade.');
      });

      it('should close without calling the service on cancel', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('Estarei fora da cidade.');
        harness.detectChanges();
        findButton('Cancelar').click();
        harness.detectChanges();

        expect(currentUserScheduleService.updateParticipation).not.toHaveBeenCalled();
        expect(card().querySelector('textarea')).toBeNull();
      });
    });

    describe('status transitions', () => {
      it('should switch from CONFIRMED to DECLINED', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [createSchedule({ eventId: 15, participationStatus: 'CONFIRMED', respondedAt: '2026-07-18T10:00:00' })],
          }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED', declineReason: 'Imprevisto.' })),
        );

        findButton('Não poderei participar').click();
        harness.detectChanges();
        setTextareaValue('Imprevisto.');
        harness.detectChanges();
        findButton('Salvar recusa').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Não participará');
      });

      it('should switch from DECLINED to CONFIRMED', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                eventId: 15,
                participationStatus: 'DECLINED',
                declineReason: 'Imprevisto.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED', declineReason: null })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Participação confirmada');
      });

      it('should keep DECLINED while changing the reason', async () => {
        await setup(
          '/minhas-escalas',
          createPage({
            content: [
              createSchedule({
                eventId: 15,
                participationStatus: 'DECLINED',
                declineReason: 'Motivo antigo.',
                respondedAt: '2026-07-18T10:00:00',
              }),
            ],
          }),
        );
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'DECLINED', declineReason: 'Motivo novo.' })),
        );

        findButton('Alterar recusa').click();
        harness.detectChanges();
        setTextareaValue('Motivo novo.');
        harness.detectChanges();
        findButton('Salvar recusa').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Não participará');
        expect(card().textContent).toContain('Motivo: Motivo novo.');
      });
    });

    describe('error handling', () => {
      it('should show a message for HTTP 400', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 400 })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain(
          'Não foi possível registrar a participação. Revise os dados informados.',
        );
      });

      it('should not create a local logout handling for HTTP 401', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        navigateSpy.calls.reset();
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 401 })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(navigateSpy).not.toHaveBeenCalled();
      });

      it('should show a message for HTTP 403', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 403 })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Você não tem permissão para responder a esta escala.');
      });

      it('should show a message for HTTP 404', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 404 })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain(
          'Não foi possível localizar o evento ou a pessoa associada à conta autenticada.',
        );
      });

      it('should show a message for HTTP 409 caused by the event having started', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 409,
                error: { error: 'Nao e possivel responder a participacao apos o inicio do evento' },
              }),
          ),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain(
          'Não é mais possível alterar a participação porque o evento já começou.',
        );
      });

      it('should show a message for HTTP 409 caused by no longer being assigned', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 409,
                error: { error: 'A pessoa autenticada nao possui atribuicao neste evento' },
              }),
          ),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(textContent()).toContain('Você não está mais escalado para este evento.');
      });

      it('should run a single reconciliation GET when the person is no longer assigned', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(
            () =>
              new HttpErrorResponse({
                status: 409,
                error: { error: 'A pessoa autenticada nao possui atribuicao neste evento' },
              }),
          ),
        );
        currentUserScheduleService.findSchedules.calls.reset();
        currentUserScheduleService.findSchedules.and.returnValue(of(createPage({ content: [] })));

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
      });

      it('should show a generic message for an unexpected error', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          throwError(() => new HttpErrorResponse({ status: 500 })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        expect(card().textContent).toContain('Não foi possível atualizar sua participação. Tente novamente.');
      });
    });

    describe('event already started (visual rule)', () => {
      it('should not allow a new visual action once the event has started', async () => {
        await setup(
          '/minhas-escalas',
          createPage({ content: [createSchedule({ eventId: 15, eventDate: '2026-07-20', eventTime: '09:00:00' })] }),
        );

        expect(card().textContent).toContain(
          'O evento já começou. A participação não pode mais ser alterada.',
        );
        expect(() => findButton('Confirmar participação')).toThrow();
        expect(() => findButton('Não poderei participar')).toThrow();
      });

      it('should use the local date and time for the rule (not UTC)', async () => {
        await setup(
          '/minhas-escalas',
          createPage({ content: [createSchedule({ eventId: 15, eventDate: '2026-07-20', eventTime: '11:00:00' })] }),
        );

        expect(card().textContent).not.toContain('O evento já começou.');
        expect(() => findButton('Confirmar participação')).not.toThrow();
      });
    });

    describe('accessibility', () => {
      it('should not depend solely on a CSS class for the status', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));

        expect(card().textContent).toContain('Aguardando resposta');
      });

      it('should associate a label and a description with the textarea', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));

        findButton('Não poderei participar').click();
        harness.detectChanges();

        const textarea = findTextarea();
        const label = card().querySelector(`label[for="${textarea.id}"]`);

        expect(label).not.toBeNull();
        expect(textarea.getAttribute('aria-describedby')).toBeTruthy();
      });

      it('should expose feedback and validation messages with appropriate roles', async () => {
        await setup('/minhas-escalas', createPage({ content: [createSchedule({ eventId: 15 })] }));
        currentUserScheduleService.updateParticipation.and.returnValue(
          of(createParticipationResponse({ eventId: 15, status: 'CONFIRMED' })),
        );

        findButton('Confirmar participação').click();
        harness.detectChanges();

        const feedback = card().querySelector('.current-user-schedules__participation-feedback');
        expect(feedback?.getAttribute('role')).toBe('status');
      });
    });
  });

  function textContent(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }
});

function createSchedule(overrides: Partial<CurrentUserSchedule> = {}): CurrentUserSchedule {
  return {
    eventId: 15,
    eventName: 'Missa das 19h',
    eventDate: '2026-07-20',
    eventTime: '19:00:00',
    massOrCelebration: true,
    locationId: 2,
    locationName: 'Igreja Matriz',
    assignments: ['READER', 'COMMENTATOR'],
    participationStatus: 'PENDING',
    declineReason: null,
    respondedAt: null,
    ...overrides,
  };
}

function createPage(overrides: Partial<CurrentUserSchedulePage> = {}): CurrentUserSchedulePage {
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

function createParticipationResponse(
  overrides: Partial<ScheduleParticipationResponse> = {},
): ScheduleParticipationResponse {
  return {
    eventId: 15,
    status: 'CONFIRMED',
    declineReason: null,
    respondedAt: '2026-07-20T10:00:00',
    ...overrides,
  };
}
