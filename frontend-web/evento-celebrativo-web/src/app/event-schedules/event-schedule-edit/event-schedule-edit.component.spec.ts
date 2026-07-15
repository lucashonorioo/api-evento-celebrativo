import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { CommentatorService } from '../../commentators/commentator.service';
import { EucharisticMinisterService } from '../../eucharistic-ministers/eucharistic-minister.service';
import { LocationService } from '../../locations/location.service';
import { MinisterOfTheWordService } from '../../ministers-of-the-word/minister-of-the-word.service';
import { PriestService } from '../../priests/priest.service';
import { ReaderService } from '../../readers/reader.service';
import {
  EventScheduleDetailResponse,
  UpdateEventScheduleResponse,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleEditComponent } from './event-schedule-edit.component';

describe('EventScheduleEditComponent', () => {
  let component: EventScheduleEditComponent;
  let fixture: ComponentFixture<EventScheduleEditComponent>;
  let commentatorService: jasmine.SpyObj<CommentatorService>;
  let eucharisticMinisterService: jasmine.SpyObj<EucharisticMinisterService>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;
  let locationService: jasmine.SpyObj<LocationService>;
  let ministerOfTheWordService: jasmine.SpyObj<MinisterOfTheWordService>;
  let priestService: jasmine.SpyObj<PriestService>;
  let readerService: jasmine.SpyObj<ReaderService>;

  async function setup(
    routeId: string | null = '1',
    schedule: EventScheduleDetailResponse = createDetail(),
    queryParams: Record<string, string> = {},
  ): Promise<void> {
    commentatorService = jasmine.createSpyObj<CommentatorService>('CommentatorService', ['findAll']);
    eucharisticMinisterService = jasmine.createSpyObj<EucharisticMinisterService>(
      'EucharisticMinisterService',
      ['findAll'],
    );
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'findByEventId',
      'updateEventSchedule',
    ]);
    locationService = jasmine.createSpyObj<LocationService>('LocationService', ['findAll']);
    ministerOfTheWordService = jasmine.createSpyObj<MinisterOfTheWordService>(
      'MinisterOfTheWordService',
      ['findAll'],
    );
    priestService = jasmine.createSpyObj<PriestService>('PriestService', ['findAll']);
    readerService = jasmine.createSpyObj<ReaderService>('ReaderService', ['findAll']);

    eventScheduleService.findByEventId.and.returnValue(of(schedule));
    eventScheduleService.updateEventSchedule.and.returnValue(of(createUpdateResponse()));
    locationService.findAll.and.returnValue(of(locations));
    priestService.findAll.and.returnValue(of(priests));
    readerService.findAll.and.returnValue(of(readers));
    commentatorService.findAll.and.returnValue(of(commentators));
    ministerOfTheWordService.findAll.and.returnValue(of(ministersOfTheWord));
    eucharisticMinisterService.findAll.and.returnValue(of(eucharisticMinisters));

    await TestBed.configureTestingModule({
      imports: [EventScheduleEditComponent],
      providers: [
        provideRouter([]),
        { provide: CommentatorService, useValue: commentatorService },
        { provide: EucharisticMinisterService, useValue: eucharisticMinisterService },
        { provide: EventScheduleService, useValue: eventScheduleService },
        { provide: LocationService, useValue: locationService },
        { provide: MinisterOfTheWordService, useValue: ministerOfTheWordService },
        { provide: PriestService, useValue: priestService },
        { provide: ReaderService, useValue: readerService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap(routeId === null ? {} : { id: routeId }),
              queryParamMap: convertToParamMap(queryParams),
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventScheduleEditComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create and render the basic edit structure', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(component).toBeTruthy();
    expect(text).toContain('Editar escala');
    expect(text).toContain('Local e padre');
    expect(text).toContain('Leitores');
    expect(text).toContain('Salvar alteracoes');
    expect(text).toContain('Cancelar');
  });

  it('should load schedule and all option services once for a valid id', async () => {
    await setup('15');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).toHaveBeenCalledOnceWith(15);
    expect(locationService.findAll).toHaveBeenCalledTimes(1);
    expect(priestService.findAll).toHaveBeenCalledTimes(1);
    expect(readerService.findAll).toHaveBeenCalledTimes(1);
    expect(commentatorService.findAll).toHaveBeenCalledTimes(1);
    expect(ministerOfTheWordService.findAll).toHaveBeenCalledTimes(1);
    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(1);
  });

  it('should not call services when route id is invalid', async () => {
    for (const routeId of [null, '', 'abc', '0', '-1', '1.5']) {
      await setup(routeId);

      fixture.detectChanges();

      expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
      expect(locationService.findAll).not.toHaveBeenCalled();
      expect(textContent()).toContain('Nao foi possivel identificar o evento solicitado');
      TestBed.resetTestingModule();
    }
  });

  it('should show loading while initial data is pending', async () => {
    const pendingSchedule = new Subject<EventScheduleDetailResponse>();
    await setup();
    eventScheduleService.findByEventId.and.returnValue(pendingSchedule);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando dados da escala');

    pendingSchedule.next(createDetail());
    pendingSchedule.complete();
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
  });

  it('should fill the form from the schedule detail', async () => {
    await setup();

    fixture.detectChanges();

    expect(component.form.controls.locationId.value).toBe(1);
    expect(component.form.controls.priestId.value).toBe(13);
    expect(component.form.controls.readerIds.value).toEqual([4, 5]);
    expect(component.form.controls.commentatorIds.value).toEqual([1]);
    expect(component.form.controls.ministerOfTheWordIds.value).toEqual([7]);
    expect(component.form.controls.eucharisticMinisterIds.value).toEqual([10]);
    expect(component.form.pristine).toBeTrue();
  });

  it('should represent nullable priest and empty lists without selecting defaults', async () => {
    await setup(
      '1',
      createDetail({
        priest: null,
        readers: [],
        commentators: [],
        ministersOfTheWord: [],
        eucharisticMinisters: [],
      }),
    );

    fixture.detectChanges();

    expect(component.form.controls.priestId.value).toBeNull();
    expect(component.form.controls.readerIds.value).toEqual([]);
    expect(component.form.controls.commentatorIds.value).toEqual([]);
    expect(component.form.controls.ministerOfTheWordIds.value).toEqual([]);
    expect(component.form.controls.eucharisticMinisterIds.value).toEqual([]);
  });

  it('should keep the form invalid when location is missing', async () => {
    await setup('1', createDetail({ location: null }));

    fixture.detectChanges();

    expect(component.form.invalid).toBeTrue();
    expect(component.form.controls.locationId.value).toBeNull();
    expect(textContent()).toContain('Selecione um local');
  });

  it('should render option names without personal data or ids', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Igreja Matriz');
    expect(text).toContain('Padre Miguel');
    expect(text).toContain('Alice Lima');
    expect(text).not.toContain('98765');
    expect(text).not.toContain('34999999991');
    expect(text).not.toContain('1990-01-10');
    expect(text).not.toContain('ROLE_ADMIN');
  });

  it('should toggle multiple selections without duplicated ids', async () => {
    await setup();
    fixture.detectChanges();

    component.toggleSelection('readerIds', 6, true);
    component.toggleSelection('readerIds', 6, true);
    component.toggleSelection('readerIds', 4, false);

    expect(component.form.controls.readerIds.value).toEqual([5, 6]);
  });

  it('should filter people locally without removing selections', async () => {
    await setup();
    fixture.detectChanges();

    component.toggleSelection('readerIds', 5, true);
    component.readerSearch.set('alice');

    expect(component.filteredReaders().map((reader) => reader.name)).toEqual(['Alice Lima']);
    expect(component.form.controls.readerIds.value).toContain(5);
  });

  it('should show empty option messages', async () => {
    await setup();
    readerService.findAll.and.returnValue(of([]));
    commentatorService.findAll.and.returnValue(of([]));
    ministerOfTheWordService.findAll.and.returnValue(of([]));
    eucharisticMinisterService.findAll.and.returnValue(of([]));

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Nenhum leitor cadastrado');
    expect(text).toContain('Nenhum comentarista cadastrado');
    expect(text).toContain('Nenhum ministro da Palavra cadastrado');
    expect(text).toContain('Nenhum ministro da Eucaristia cadastrado');
  });

  it('should not submit an invalid form', async () => {
    await setup('1', createDetail({ location: null }));

    fixture.detectChanges();
    component.onSubmit();

    expect(eventScheduleService.updateEventSchedule).not.toHaveBeenCalled();
    expect(component.form.controls.locationId.touched).toBeTrue();
  });

  it('should submit the expected request and no extra fields', async () => {
    await setup();
    fixture.detectChanges();

    component.form.controls.readerIds.setValue([4, 4, 5]);
    component.onSubmit();

    expect(eventScheduleService.updateEventSchedule).toHaveBeenCalledOnceWith(1, {
      locationId: 1,
      priestId: 13,
      readerIds: [4, 5],
      commentatorIds: [1],
      ministerOfTheWordIds: [7],
      eucharisticMinisterIds: [10],
    });
    expect(JSON.stringify(eventScheduleService.updateEventSchedule.calls.mostRecent().args[1])).not.toContain(
      'eventId',
    );
  });

  it('should allow empty participant lists on submit', async () => {
    await setup(
      '1',
      createDetail({
        readers: [],
        commentators: [],
        ministersOfTheWord: [],
        eucharisticMinisters: [],
      }),
    );
    fixture.detectChanges();

    component.onSubmit();

    expect(eventScheduleService.updateEventSchedule).toHaveBeenCalledOnceWith(1, {
      locationId: 1,
      priestId: 13,
      readerIds: [],
      commentatorIds: [],
      ministerOfTheWordIds: [],
      eucharisticMinisterIds: [],
    });
  });

  it('should prevent duplicated save while request is pending', async () => {
    const pendingSave = new Subject<UpdateEventScheduleResponse>();
    await setup();
    eventScheduleService.updateEventSchedule.and.returnValue(pendingSave);
    fixture.detectChanges();

    component.onSubmit();
    component.onSubmit();

    expect(eventScheduleService.updateEventSchedule).toHaveBeenCalledTimes(1);

    pendingSave.next(createUpdateResponse());
    pendingSave.complete();
  });

  it('should show success and mark form pristine after saving', async () => {
    await setup();
    fixture.detectChanges();

    component.form.markAsDirty();
    component.onSubmit();
    fixture.detectChanges();

    expect(textContent()).toContain('Escala atualizada com sucesso');
    expect(component.form.pristine).toBeTrue();
    expect(component.schedule()?.eventName).toBe('Missa atualizada');
  });

  it('should preserve form values after save error', async () => {
    await setup();
    eventScheduleService.updateEventSchedule.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para atualizar esta escala');
    expect(component.form.controls.locationId.value).toBe(1);
    expect(component.form.controls.readerIds.value).toEqual([4, 5]);
  });

  it('should show save messages for 400, 404, 409 and generic errors', async () => {
    const cases = [
      { status: 400, message: 'Revise os dados da escala' },
      { status: 404, message: 'A escala do evento solicitado nao foi encontrada' },
      { status: 409, message: 'conflito com os dados atuais' },
      { status: 500, message: 'Nao foi possivel atualizar a escala' },
    ];

    for (const testCase of cases) {
      await setup();
      eventScheduleService.updateEventSchedule.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: testCase.status })),
      );
      fixture.detectChanges();

      component.onSubmit();
      fixture.detectChanges();

      expect(textContent()).toContain(testCase.message);
      TestBed.resetTestingModule();
    }
  });

  it('should show load errors and retry the full load', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(createDetail()),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar os dados da escala');

    const retryButton = fixture.nativeElement.querySelector(
      '.event-schedule-edit__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).toHaveBeenCalledTimes(2);
    expect(locationService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Missa de Domingo da manha');
  });

  it('should show specific load messages for 404 and 403', async () => {
    const cases = [
      { status: 404, message: 'A escala do evento solicitado nao foi encontrada' },
      { status: 403, message: 'Voce nao possui permissao para editar esta escala' },
    ];

    for (const testCase of cases) {
      await setup();
      eventScheduleService.findByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: testCase.status })),
      );
      fixture.detectChanges();
      fixture.detectChanges();

      expect(textContent()).toContain(testCase.message);
      TestBed.resetTestingModule();
    }
  });

  it('should cancel to the detail preserving query params without saving', async () => {
    await setup('1', createDetail(), {
      type: 'READER',
      month: '2026-07',
      includeUnassigned: 'true',
      page: '2',
    });
    const router = TestBed.inject(Router);
    const navigateSpy = spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();

    component.cancel();

    expect(eventScheduleService.updateEventSchedule).not.toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledOnceWith(['/app/escalas/eventos', 1], {
      queryParams: {
        type: 'READER',
        month: '2026-07',
        includeUnassigned: 'true',
        page: '2',
      },
    });
  });

  it('should preserve valid query params in the detail link', async () => {
    await setup('1', createDetail(), {
      type: 'READER',
      month: '2026-07',
      includeUnassigned: 'false',
      page: '2',
    });

    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;
    const href = link.getAttribute('href') ?? '';

    expect(href).toContain('/app/escalas/eventos/1');
    expect(href).toContain('type=READER');
    expect(href).toContain('month=2026-07');
    expect(href).toContain('includeUnassigned=false');
    expect(href).toContain('page=2');
  });

  it('should not render creation, deletion or raw identifiers', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).not.toContain('Novo evento');
    expect(text).not.toContain('Criar evento');
    expect(text).not.toContain('Excluir');
    expect(text).not.toContain('eventId');
    expect(text).not.toContain('locationId');
    expect(text).not.toContain('personId');
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});

const locations = [
  { id: 1, churchName: 'Igreja Matriz', address: 'Rua Central' },
  { id: 2, churchName: 'Capela Sao Jose', address: 'Rua Dois' },
];

const priests = [
  { id: 13, name: 'Padre Miguel', phoneNumber: '34999999991', birthdayDate: '1970-01-10' },
  { id: 14, name: 'Padre Antonio', phoneNumber: null, birthdayDate: null },
];

const readers = [
  { id: 4, name: 'Alice Lima', phoneNumber: '34999999992', birthdayDate: '1990-01-10' },
  { id: 5, name: 'Arthur Costa', phoneNumber: null, birthdayDate: null },
  { id: 6, name: 'Bruna Reis', phoneNumber: null, birthdayDate: null },
];

const commentators = [
  { id: 1, name: 'Luana Odinson', phoneNumber: null, birthdayDate: null },
];

const ministersOfTheWord = [
  { id: 7, name: 'Davi Gomes', phoneNumber: null, birthdayDate: null },
];

const eucharisticMinisters = [
  { id: 10, name: 'Mariana Ferraz', phoneNumber: null, birthdayDate: null },
];

function createDetail(
  overrides: Partial<EventScheduleDetailResponse> = {},
): EventScheduleDetailResponse {
  return {
    eventId: 1,
    eventName: 'Missa de Domingo da manha',
    eventDate: '2025-07-13',
    eventTime: '10:00:00',
    massOrCelebration: true,
    location: {
      id: 1,
      churchName: 'Igreja Matriz',
    },
    priest: {
      id: 13,
      name: 'Padre Miguel',
    },
    readers: [
      { id: 4, name: 'Alice Lima' },
      { id: 5, name: 'Arthur Costa' },
    ],
    commentators: [{ id: 1, name: 'Luana Odinson' }],
    ministersOfTheWord: [{ id: 7, name: 'Davi Gomes' }],
    eucharisticMinisters: [{ id: 10, name: 'Mariana Ferraz' }],
    ...overrides,
  };
}

function createUpdateResponse(
  overrides: Partial<UpdateEventScheduleResponse> = {},
): UpdateEventScheduleResponse {
  return {
    eventId: 1,
    nameMassOrEvent: 'Missa atualizada',
    eventDate: '2025-07-13',
    eventTime: '10:00:00',
    massOrCelebration: true,
    location: {
      id: 1,
      churchName: 'Igreja Matriz',
    },
    priest: {
      id: 13,
      name: 'Padre Miguel',
    },
    readers: [
      { id: 4, name: 'Alice Lima' },
      { id: 5, name: 'Arthur Costa' },
    ],
    commentators: [{ id: 1, name: 'Luana Odinson' }],
    ministersOfTheWord: [{ id: 7, name: 'Davi Gomes' }],
    eucharisticMinisters: [{ id: 10, name: 'Mariana Ferraz' }],
    ...overrides,
  };
}
