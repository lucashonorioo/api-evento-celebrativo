import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import {
  EventSchedulePage,
  EventScheduleQuery,
  EventScheduleResponse,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleListComponent } from './event-schedule-list.component';

describe('EventScheduleListComponent', () => {
  let component: EventScheduleListComponent;
  let fixture: ComponentFixture<EventScheduleListComponent>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;

  async function setup(
    response: EventSchedulePage = createPage(),
    queryParams: Record<string, string> = {},
  ): Promise<void> {
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'findMonthlySchedules',
    ]);
    eventScheduleService.findMonthlySchedules.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      imports: [EventScheduleListComponent],
      providers: [
        provideRouter([]),
        { provide: EventScheduleService, useValue: eventScheduleService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(queryParams),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventScheduleListComponent);
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

  it('should load reader schedules for the current local month on initialization', async () => {
    await setup();

    fixture.detectChanges();

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

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Consulta mensal de escalas');
    expect(text).toContain('Missa Dominical');
    expect(text).toContain('Igreja Matriz');
    expect(text).toContain('20/07/2026');
    expect(text).toContain('19:00');
    expect(text).toContain('Maria da Silva');
    expect(text).toContain('Leitores');
  });

  it('should render a link to the full schedule detail without showing ids as text', async () => {
    await setup();

    fixture.detectChanges();

    const detailLink = fixture.nativeElement.querySelector(
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

  it('should render a message when an event has no assignments', async () => {
    await setup(
      createPage({
        content: [createSchedule({ assignments: [] })],
      }),
    );

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhuma pessoa foi escalada para esta funcao');
  });

  it('should render the empty state when the API returns no schedules', async () => {
    await setup(createPage({ content: [], totalElements: 0, numberOfElements: 0, empty: true }));

    fixture.detectChanges();

    expect(textContent()).toContain('Nenhuma escala de Leitores foi encontrada');
  });

  it('should render a loading state while the request is pending', async () => {
    const pendingRequest = new Subject<EventSchedulePage>();
    await setup();
    eventScheduleService.findMonthlySchedules.and.returnValue(pendingRequest);

    fixture.detectChanges();

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

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('carregar as escalas');

    const retryButton = fixture.nativeElement.querySelector(
      '.event-schedules__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

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

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para consultar as escalas');
  });

  it('should not request the API when the selected month is invalid', async () => {
    await setup();
    fixture.detectChanges();
    eventScheduleService.findMonthlySchedules.calls.reset();

    component.selectedMonth = '2026';
    component.onSubmit();
    fixture.detectChanges();

    expect(eventScheduleService.findMonthlySchedules).not.toHaveBeenCalled();
    expect(textContent()).toContain('mes valido');
  });

  it('should request page zero with selected filters when submitting a valid month', async () => {
    await setup();
    fixture.detectChanges();
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
    await setup(createPage(), {
      type: 'PRIEST',
      month: '2026-08',
      includeUnassigned: 'true',
      page: '2',
    });

    fixture.detectChanges();

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: '2026-08-01',
      endDate: '2026-08-31',
      type: 'PRIEST',
      page: 2,
      size: 10,
      includeUnassigned: true,
    });
  });

  it('should use defaults when query params are invalid', async () => {
    await setup(createPage(), {
      type: 'INVALID',
      month: '2026',
      includeUnassigned: 'maybe',
      page: '-1',
    });

    fixture.detectChanges();

    expect(eventScheduleService.findMonthlySchedules).toHaveBeenCalledOnceWith({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      type: 'READER',
      page: 0,
      size: 10,
      includeUnassigned: false,
    });
  });

  it('should navigate to the previous and next month from the selected month', async () => {
    await setup();
    fixture.detectChanges();
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
    await setup(createPage({ totalPages: 2, first: true, last: false }));
    fixture.detectChanges();
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
    await setup(createPage({ totalPages: 1, first: true, last: true }));

    fixture.detectChanges();

    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('.schedule-pagination button'),
    ) as HTMLButtonElement[];

    expect(buttons[0].disabled).toBeTrue();
    expect(buttons[1].disabled).toBeTrue();
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
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
