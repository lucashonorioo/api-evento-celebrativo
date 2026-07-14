import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import {
  EucharistSchedulePage,
  EucharistScheduleQuery,
  EucharistScheduleResponse,
} from '../eucharist-schedule.models';
import { EucharistScheduleService } from '../eucharist-schedule.service';
import { EucharistScheduleListComponent } from './eucharist-schedule-list.component';

describe('EucharistScheduleListComponent', () => {
  let component: EucharistScheduleListComponent;
  let fixture: ComponentFixture<EucharistScheduleListComponent>;
  let eucharistScheduleService: jasmine.SpyObj<EucharistScheduleService>;

  const schedule: EucharistScheduleResponse = {
    nameMassOrEvent: 'Missa Dominical',
    eventDate: '2026-08-15',
    eventTime: '19:30:00',
    churchName: 'Igreja Matriz',
    nameMinisters: ['Maria Silva', 'Joao Pereira'],
  };

  async function setup(response: EucharistSchedulePage = createPage()): Promise<void> {
    eucharistScheduleService = jasmine.createSpyObj<EucharistScheduleService>(
      'EucharistScheduleService',
      ['findEucharistSchedule'],
    );
    eucharistScheduleService.findEucharistSchedule.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      imports: [EucharistScheduleListComponent],
      providers: [{ provide: EucharistScheduleService, useValue: eucharistScheduleService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EucharistScheduleListComponent);
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

  it('should load the current local month on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(eucharistScheduleService.findEucharistSchedule).toHaveBeenCalledOnceWith({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      page: 0,
      size: 10,
    });
  });

  it('should render the returned schedules', async () => {
    await setup();

    fixture.detectChanges();

    const textContent = getTextContent();

    expect(textContent).toContain('Missa Dominical');
    expect(textContent).toContain('Igreja Matriz');
    expect(textContent).toContain('15/08/2026');
    expect(textContent).toContain('19:30');
    expect(textContent).toContain('Maria Silva');
    expect(textContent).toContain('Joao Pereira');
  });

  it('should render the empty state when the API returns no schedules', async () => {
    await setup(createPage({ content: [], totalElements: 0, totalPages: 0, empty: true }));

    fixture.detectChanges();

    expect(getTextContent()).toContain('Nenhuma escala de Eucaristia foi encontrada');
  });

  it('should render a loading state while the request is pending', async () => {
    const pendingRequest = new Subject<EucharistSchedulePage>();
    await setup();
    eucharistScheduleService.findEucharistSchedule.and.returnValue(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(getTextContent()).toContain('Carregando escala...');

    pendingRequest.next(createPage());
    pendingRequest.complete();
  });

  it('should show an error message and allow retrying the last valid query', async () => {
    await setup();
    eucharistScheduleService.findEucharistSchedule.and.returnValues(
      throwError(() => new Error('Failed to load schedule')),
      of(createPage()),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(getTextContent()).toContain('carregar a escala');

    const retryButton = fixture.nativeElement.querySelector(
      '.eucharist-schedule__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(eucharistScheduleService.findEucharistSchedule).toHaveBeenCalledTimes(2);
    expect(getTextContent()).toContain('Missa Dominical');
  });

  it('should not request the API when the selected period is invalid', async () => {
    await setup();
    fixture.detectChanges();
    eucharistScheduleService.findEucharistSchedule.calls.reset();

    component.startDate = '2026-08-31';
    component.endDate = '2026-08-01';
    component.onSubmit();
    fixture.detectChanges();

    expect(eucharistScheduleService.findEucharistSchedule).not.toHaveBeenCalled();
    expect(getTextContent()).toContain('data inicial');
    expect(getTextContent()).toContain('posterior');
  });

  it('should request page zero when submitting a new valid period', async () => {
    await setup();
    fixture.detectChanges();
    eucharistScheduleService.findEucharistSchedule.calls.reset();

    component.startDate = '2026-09-01';
    component.endDate = '2026-09-30';
    component.onSubmit();

    expect(eucharistScheduleService.findEucharistSchedule).toHaveBeenCalledOnceWith({
      startDate: '2026-09-01',
      endDate: '2026-09-30',
      page: 0,
      size: 10,
    });
  });

  it('should request the next page while preserving the selected period and size', async () => {
    await setup(createPage({ totalPages: 2, first: true, last: false }));
    fixture.detectChanges();
    eucharistScheduleService.findEucharistSchedule.calls.reset();
    eucharistScheduleService.findEucharistSchedule.and.returnValue(
      of(createPage({ number: 1, totalPages: 2, first: false, last: true })),
    );

    component.nextPage();

    const query = eucharistScheduleService.findEucharistSchedule.calls.mostRecent()
      .args[0] as EucharistScheduleQuery;

    expect(query).toEqual({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      page: 1,
      size: 10,
    });
  });

  it('should request the previous page while preserving the selected period and size', async () => {
    await setup(createPage({ number: 1, totalPages: 2, first: false, last: true }));
    fixture.detectChanges();
    eucharistScheduleService.findEucharistSchedule.calls.reset();
    eucharistScheduleService.findEucharistSchedule.and.returnValue(
      of(createPage({ number: 0, totalPages: 2, first: true, last: false })),
    );

    component.previousPage();

    const query = eucharistScheduleService.findEucharistSchedule.calls.mostRecent()
      .args[0] as EucharistScheduleQuery;

    expect(query).toEqual({
      startDate: firstDayOfCurrentMonth(),
      endDate: lastDayOfCurrentMonth(),
      page: 0,
      size: 10,
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

  function getTextContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});

function createPage(overrides: Partial<EucharistSchedulePage> = {}): EucharistSchedulePage {
  return {
    content: [
      {
        nameMassOrEvent: 'Missa Dominical',
        eventDate: '2026-08-15',
        eventTime: '19:30:00',
        churchName: 'Igreja Matriz',
        nameMinisters: ['Maria Silva', 'Joao Pereira'],
      },
    ],
    number: 0,
    size: 10,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
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
