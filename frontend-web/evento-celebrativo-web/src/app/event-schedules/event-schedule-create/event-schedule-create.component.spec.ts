import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { CommentatorService } from '../../commentators/commentator.service';
import { EucharisticMinisterService } from '../../eucharistic-ministers/eucharistic-minister.service';
import { LocationResponse } from '../../locations/location.models';
import { LocationService } from '../../locations/location.service';
import { MinisterOfTheWordService } from '../../ministers-of-the-word/minister-of-the-word.service';
import { PriestService } from '../../priests/priest.service';
import { ReaderService } from '../../readers/reader.service';
import {
  CreateEventWithScheduleRequest,
  CreateEventWithScheduleResponse,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleCreateComponent } from './event-schedule-create.component';

describe('EventScheduleCreateComponent', () => {
  let component: EventScheduleCreateComponent;
  let fixture: ComponentFixture<EventScheduleCreateComponent>;
  let commentatorService: jasmine.SpyObj<CommentatorService>;
  let eucharisticMinisterService: jasmine.SpyObj<EucharisticMinisterService>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;
  let locationService: jasmine.SpyObj<LocationService>;
  let ministerOfTheWordService: jasmine.SpyObj<MinisterOfTheWordService>;
  let priestService: jasmine.SpyObj<PriestService>;
  let readerService: jasmine.SpyObj<ReaderService>;
  let router: Router;

  async function setup(): Promise<void> {
    commentatorService = jasmine.createSpyObj<CommentatorService>('CommentatorService', ['findAll']);
    eucharisticMinisterService = jasmine.createSpyObj<EucharisticMinisterService>(
      'EucharisticMinisterService',
      ['findAll'],
    );
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'createEventWithSchedule',
    ]);
    locationService = jasmine.createSpyObj<LocationService>('LocationService', ['findAll']);
    ministerOfTheWordService = jasmine.createSpyObj<MinisterOfTheWordService>(
      'MinisterOfTheWordService',
      ['findAll'],
    );
    priestService = jasmine.createSpyObj<PriestService>('PriestService', ['findAll']);
    readerService = jasmine.createSpyObj<ReaderService>('ReaderService', ['findAll']);

    commentatorService.findAll.and.returnValue(
      of([{ id: 4, name: 'Carla Souza', phoneNumber: '4444', birthdayDate: '1994-04-04' }]),
    );
    eucharisticMinisterService.findAll.and.returnValue(
      of([{ id: 8, name: 'Helena Costa', phoneNumber: '8888', birthdayDate: '1988-08-08' }]),
    );
    eventScheduleService.createEventWithSchedule.and.returnValue(of(createResponse()));
    locationService.findAll.and.returnValue(
      of([{ id: 2, churchName: 'Igreja Matriz', address: 'Rua Central' }]),
    );
    ministerOfTheWordService.findAll.and.returnValue(
      of([{ id: 6, name: 'Davi Gomes', phoneNumber: '6666', birthdayDate: '1996-06-06' }]),
    );
    priestService.findAll.and.returnValue(
      of([{ id: 3, name: 'Padre Antonio', phoneNumber: '3333', birthdayDate: '1980-03-03' }]),
    );
    readerService.findAll.and.returnValue(
      of([
        { id: 5, name: 'Alice Lima', phoneNumber: '5555', birthdayDate: '1995-05-05' },
        { id: 7, name: 'Bruno Dias', phoneNumber: '7777', birthdayDate: '1997-07-07' },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [EventScheduleCreateComponent],
      providers: [
        provideRouter([]),
        { provide: CommentatorService, useValue: commentatorService },
        { provide: EucharisticMinisterService, useValue: eucharisticMinisterService },
        { provide: EventScheduleService, useValue: eventScheduleService },
        { provide: LocationService, useValue: locationService },
        { provide: MinisterOfTheWordService, useValue: ministerOfTheWordService },
        { provide: PriestService, useValue: priestService },
        { provide: ReaderService, useValue: readerService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(EventScheduleCreateComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create and load all auxiliary options on initialization', async () => {
    await setup();

    fixture.detectChanges();

    expect(component).toBeTruthy();
    expect(locationService.findAll).toHaveBeenCalledTimes(1);
    expect(priestService.findAll).toHaveBeenCalledTimes(1);
    expect(readerService.findAll).toHaveBeenCalledTimes(1);
    expect(commentatorService.findAll).toHaveBeenCalledTimes(1);
    expect(ministerOfTheWordService.findAll).toHaveBeenCalledTimes(1);
    expect(eucharisticMinisterService.findAll).toHaveBeenCalledTimes(1);
    expect(textContent()).toContain('Novo evento com escala');
    expect(textContent()).toContain('Igreja Matriz');
    expect(textContent()).toContain('Padre Antonio');
    expect(textContent()).toContain('Alice Lima');
  });

  it('should not render ids or personal data from auxiliary options', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).not.toContain('phoneNumber');
    expect(text).not.toContain('birthdayDate');
    expect(text).not.toContain('5555');
    expect(text).not.toContain('1995-05-05');
    expect(text).not.toContain('readerIds');
    expect(text).not.toContain('locationId');
  });

  it('should show loading and retry option loading failures', async () => {
    const pendingLocations = new Subject<LocationResponse[]>();
    await setup();
    locationService.findAll.and.returnValue(pendingLocations);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando dados do formulario');

    pendingLocations.error(new Error('failed'));
    fixture.detectChanges();

    expect(textContent()).toContain('carregar os dados do formulario');

    locationService.findAll.and.returnValue(
      of([{ id: 2, churchName: 'Igreja Matriz', address: 'Rua Central' }]),
    );
    const retryButton = fixture.nativeElement.querySelector(
      '.event-schedule-create__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(locationService.findAll).toHaveBeenCalledTimes(2);
  });

  it('should keep the form invalid while required fields are missing', async () => {
    await setup();
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    expect(eventScheduleService.createEventWithSchedule).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe o nome do evento');
    expect(textContent()).toContain('Informe a data do evento');
    expect(textContent()).toContain('Informe o horario do evento');
    expect(textContent()).toContain('Informe um local para a escala');
  });

  it('should create with the expected payload and no extra event fields', async () => {
    await setup();
    fixture.detectChanges();
    fillValidForm();
    component.toggleSelection('readerIds', 5, true);
    component.toggleSelection('readerIds', 5, true);
    component.toggleSelection('commentatorIds', 4, true);
    component.toggleSelection('ministerOfTheWordIds', 6, true);
    component.toggleSelection('eucharisticMinisterIds', 8, true);

    component.onSubmit();

    const request = eventScheduleService.createEventWithSchedule.calls.mostRecent()
      .args[0] as CreateEventWithScheduleRequest;

    expect(request).toEqual({
      nameMassOrEvent: 'Missa da Comunidade',
      eventDate: '2026-08-15',
      eventTime: '19:30:00',
      massOrCelebration: true,
      locationId: 2,
      priestId: 3,
      readerIds: [5],
      commentatorIds: [4],
      ministerOfTheWordIds: [6],
      eucharisticMinisterIds: [8],
    });
    expect(Object.keys(request)).not.toContain('eventId');
    expect(Object.keys(request)).not.toContain('eventName');
    expect(Object.keys(request)).not.toContain('name');
    expect(Object.keys(request)).not.toContain('location');
    expect(Object.keys(request)).not.toContain('readers');
  });

  it('should allow creating without priest and with empty participant lists', async () => {
    await setup();
    fixture.detectChanges();
    fillValidForm();
    component.form.controls.priestId.setValue(null);

    component.onSubmit();

    expect(eventScheduleService.createEventWithSchedule).toHaveBeenCalledOnceWith({
      nameMassOrEvent: 'Missa da Comunidade',
      eventDate: '2026-08-15',
      eventTime: '19:30:00',
      massOrCelebration: true,
      locationId: 2,
      priestId: null,
      readerIds: [],
      commentatorIds: [],
      ministerOfTheWordIds: [],
      eucharisticMinisterIds: [],
    });
  });

  it('should preserve selected people when filtering local search', async () => {
    await setup();
    fixture.detectChanges();

    component.toggleSelection('readerIds', 5, true);
    component.setSearch('readers', inputEvent('bruno'));
    fixture.detectChanges();

    expect(component.filteredReaders().map((reader) => reader.name)).toEqual(['Bruno Dias']);
    expect(component.isSelected('readerIds', 5)).toBeTrue();

    component.setSearch('readers', inputEvent(''));

    expect(component.filteredReaders().length).toBe(2);
    expect(readerService.findAll).toHaveBeenCalledTimes(1);
  });

  it('should remove selected people without creating duplicate ids', async () => {
    await setup();
    fixture.detectChanges();

    component.toggleSelection('readerIds', 5, true);
    component.toggleSelection('readerIds', 5, true);
    component.toggleSelection('readerIds', 7, true);
    component.toggleSelection('readerIds', 5, false);

    expect(component.form.controls.readerIds.value).toEqual([7]);
    expect(component.selectedCount('readerIds')).toBe(1);
  });

  it('should prevent duplicate submissions while saving', async () => {
    const pendingSave = new Subject<CreateEventWithScheduleResponse>();
    await setup();
    eventScheduleService.createEventWithSchedule.and.returnValue(pendingSave);
    fixture.detectChanges();
    fillValidForm();

    component.onSubmit();
    component.onSubmit();

    expect(eventScheduleService.createEventWithSchedule).toHaveBeenCalledTimes(1);

    pendingSave.next(createResponse());
    pendingSave.complete();
  });

  it('should show success and navigate to the created schedule detail when eventId exists', async () => {
    await setup();
    fixture.detectChanges();
    fillValidForm();

    component.onSubmit();

    expect(component.successMessage()).toBe('Evento e escala cadastrados com sucesso.');
    expect(router.navigate).toHaveBeenCalledWith(['/app/escalas/eventos', '22']);
  });

  it('should navigate to the monthly schedule list when response has no valid event id', async () => {
    await setup();
    eventScheduleService.createEventWithSchedule.and.returnValue(
      of({ ...createResponse(), eventId: 0 }),
    );
    fixture.detectChanges();
    fillValidForm();

    component.onSubmit();

    expect(router.navigate).toHaveBeenCalledWith(['/app/escalas']);
  });

  it('should show a friendly error and keep form values when saving fails', async () => {
    await setup();
    eventScheduleService.createEventWithSchedule.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            statusText: 'Conflict',
          }),
      ),
    );
    fixture.detectChanges();
    fillValidForm();

    component.onSubmit();
    fixture.detectChanges();

    expect(component.saveErrorMessage()).toContain('conflito');
    expect(component.form.controls.nameMassOrEvent.value).toBe('Missa da Comunidade');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('should map known save errors to friendly messages', async () => {
    const cases: readonly { readonly status: number; readonly expected: string }[] = [
      { status: 400, expected: 'Revise os dados' },
      { status: 403, expected: 'permissao' },
      { status: 404, expected: 'cadastro selecionado' },
      { status: 409, expected: 'conflito' },
      { status: 422, expected: 'participantes selecionados' },
      { status: 500, expected: 'Tente novamente' },
    ];

    for (const item of cases) {
      await setup();
      eventScheduleService.createEventWithSchedule.and.returnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: item.status,
              statusText: 'Error',
            }),
        ),
      );
      fixture.detectChanges();
      fillValidForm();

      component.onSubmit();

      expect(component.saveErrorMessage()).toContain(item.expected);

      TestBed.resetTestingModule();
    }
  });

  function fillValidForm(): void {
    component.form.patchValue({
      nameMassOrEvent: 'Missa da Comunidade',
      eventDate: '2026-08-15',
      eventTime: '19:30',
      massOrCelebration: true,
      locationId: 2,
      priestId: 3,
    });
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});

function createResponse(): CreateEventWithScheduleResponse {
  return {
    eventId: 22,
    nameMassOrEvent: 'Missa da Comunidade',
    eventDate: '2026-08-15',
    eventTime: '19:30:00',
    massOrCelebration: true,
    location: {
      id: 2,
      churchName: 'Igreja Matriz',
    },
    priest: {
      id: 3,
      name: 'Padre Antonio',
    },
    readers: [{ id: 5, name: 'Alice Lima' }],
    commentators: [],
    ministersOfTheWord: [],
    eucharisticMinisters: [],
  };
}

function inputEvent(value: string): Event {
  const input = document.createElement('input');
  input.value = value;

  return { target: input } as unknown as Event;
}
