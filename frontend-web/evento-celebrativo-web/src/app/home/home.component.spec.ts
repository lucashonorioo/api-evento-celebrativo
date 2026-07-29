import { HttpErrorResponse } from '@angular/common/http';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { CurrentUserProfile } from '../current-user-profile/current-user-profile.models';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';
import {
  CurrentUserSchedule,
  CurrentUserSchedulePage,
} from '../current-user-schedules/current-user-schedule.models';
import { CurrentUserScheduleService } from '../current-user-schedules/current-user-schedule.service';
import { CelebrationEventResponse } from '../events/event.models';
import { EventService } from '../events/event.service';
import { HomeComponent } from './home.component';

const NOW = new Date(2026, 6, 20, 10, 0, 0);

interface CurrentUserProfileServiceDouble {
  readonly profile: WritableSignal<CurrentUserProfile | null>;
  readonly isLoading: WritableSignal<boolean>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly loadProfile: jasmine.Spy;
  readonly updateProfile: jasmine.Spy;
  readonly clearProfile: jasmine.Spy;
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventService: jasmine.SpyObj<EventService>;
  let currentUserProfileService: CurrentUserProfileServiceDouble;
  let currentUserScheduleService: jasmine.SpyObj<CurrentUserScheduleService>;

  async function setup(
    isAdmin = false,
    events$: Observable<CelebrationEventResponse[]> = of([createEvent()]),
    now: Date = NOW,
    profileOptions: {
      profile?: CurrentUserProfile | null;
      isLoading?: boolean;
      errorMessage?: string | null;
    } = {},
    userSchedules$: Observable<CurrentUserSchedulePage> = of(createSchedulePage({ content: [] })),
  ): Promise<void> {
    jasmine.clock().mockDate(now);

    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);

    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(events$);

    currentUserProfileService = {
      profile: signal(profileOptions.profile === undefined ? createProfile() : profileOptions.profile),
      isLoading: signal(profileOptions.isLoading ?? false),
      errorMessage: signal(profileOptions.errorMessage ?? null),
      loadProfile: jasmine.createSpy('loadProfile'),
      updateProfile: jasmine.createSpy('updateProfile'),
      clearProfile: jasmine.createSpy('clearProfile'),
    };

    currentUserScheduleService = jasmine.createSpyObj<CurrentUserScheduleService>(
      'CurrentUserScheduleService',
      ['findSchedules'],
    );
    currentUserScheduleService.findSchedules.and.returnValue(userSchedules$);

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
        { provide: CurrentUserProfileService, useValue: currentUserProfileService },
        { provide: CurrentUserScheduleService, useValue: currentUserScheduleService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    jasmine.clock().install();
  });

  afterEach(() => {
    jasmine.clock().uninstall();
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the greeting with the profile name', async () => {
    await setup(false, of([createEvent()]), NOW, { profile: createProfile({ name: 'Maria Silva' }) });

    const text = textContent();

    expect(text).toContain('Painel da Paróquia');
    expect(text).toContain('Bem-vindo, Maria Silva.');
    expect(text).toContain(
      'Acompanhe os próximos eventos e acesse rapidamente as principais áreas do sistema.',
    );
  });

  it('should never render the phone number as the greeting name', async () => {
    await setup(false, of([createEvent()]), NOW, {
      profile: createProfile({ name: 'Maria Silva', phoneNumber: '34999999999' }),
    });

    expect(textContent()).toContain('Bem-vindo, Maria Silva.');
    expect(textContent()).not.toContain('Bem-vindo, 34999999999');
  });

  it('should show a neutral greeting while the profile is loading', async () => {
    await setup(false, of([createEvent()]), NOW, { profile: null, isLoading: true });

    expect(textContent()).toContain('Bem-vindo.');
    expect(textContent()).not.toContain('Bem-vindo, ');
  });

  it('should show a neutral greeting when the profile fails to load', async () => {
    await setup(false, of([createEvent()]), NOW, {
      profile: null,
      errorMessage: 'Não foi possível carregar o seu perfil. Tente novamente.',
    });

    expect(textContent()).toContain('Bem-vindo.');
    expect(textContent()).not.toContain('Bem-vindo, ');
  });

  it('should update the greeting immediately when the profile signal changes', async () => {
    await setup(false, of([createEvent()]), NOW, { profile: createProfile({ name: 'Maria Silva' }) });

    expect(textContent()).toContain('Bem-vindo, Maria Silva.');

    currentUserProfileService.profile.set(createProfile({ name: 'João Atualizado' }));
    fixture.detectChanges();

    expect(textContent()).toContain('Bem-vindo, João Atualizado.');
    expect(textContent()).not.toContain('Bem-vindo, Maria Silva.');
  });

  it('should not call the current user profile service to load the profile', async () => {
    await setup();

    expect(currentUserProfileService.loadProfile).not.toHaveBeenCalled();
  });

  it('should call EventService.findAll once on initialization', async () => {
    await setup();

    expect(eventService.findAll).toHaveBeenCalledTimes(1);
  });

  it('should show the loading state while events are pending', async () => {
    await setup();
    const pending = new Subject<CelebrationEventResponse[]>();
    eventService.findAll.and.returnValue(pending);

    fixture.componentInstance.loadEvents();
    fixture.detectChanges();

    expect(fixture.componentInstance.isLoadingEvents()).toBeTrue();
    expect(textContent()).toContain('Carregando próximos eventos...');

    pending.next([]);
    pending.complete();
    fixture.detectChanges();

    expect(fixture.componentInstance.isLoadingEvents()).toBeFalse();
  });

  it('should exclude events that have already happened', async () => {
    const pastEvent = createEvent({ id: 1, eventDate: '2026-07-19', eventTime: '23:59:59' });
    const futureEvent = createEvent({ id: 2, eventDate: '2026-07-21', eventTime: '08:00' });

    await setup(false, of([pastEvent, futureEvent]));

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([2]);
  });

  it('should include an event happening exactly at the current moment', async () => {
    const nowEvent = createEvent({ id: 9, eventDate: '2026-07-20', eventTime: '10:00' });

    await setup(false, of([nowEvent]));

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([9]);
  });

  it('should keep an event a few seconds ahead of the current moment', async () => {
    const now = new Date(2026, 6, 20, 19, 30, 30);
    const soonEvent = createEvent({ id: 11, eventDate: '2026-07-20', eventTime: '19:30:45' });

    await setup(false, of([soonEvent]), now);

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([11]);
  });

  it('should exclude an event a few seconds behind the current moment', async () => {
    const now = new Date(2026, 6, 20, 19, 30, 30);
    const pastEvent = createEvent({ id: 12, eventDate: '2026-07-20', eventTime: '19:30:15' });

    await setup(false, of([pastEvent]), now);

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([]);
  });

  it('should interpret an HH:mm event time with zero seconds', async () => {
    const now = new Date(2026, 6, 20, 19, 30, 0);
    const eventWithoutSeconds = createEvent({ id: 13, eventDate: '2026-07-20', eventTime: '19:30' });

    await setup(false, of([eventWithoutSeconds]), now);

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([13]);
  });

  it('should include an event happening in the exact same second as the current moment', async () => {
    const now = new Date(2026, 6, 20, 19, 30, 30);
    const sameSecondEvent = createEvent({ id: 14, eventDate: '2026-07-20', eventTime: '19:30:30' });

    await setup(false, of([sameSecondEvent]), now);

    expect(fixture.componentInstance.upcomingEvents().map((event) => event.id)).toEqual([14]);
  });

  it('should sort upcoming events by ascending date and time, limited to five', async () => {
    const events = [
      createEvent({ id: 1, nameMassOrEvent: 'Evento passado', eventDate: '2026-07-19', eventTime: '10:00:00' }),
      createEvent({ id: 7, nameMassOrEvent: 'Evento E', eventDate: '2026-08-01', eventTime: '07:30' }),
      createEvent({ id: 4, nameMassOrEvent: 'Evento A', eventDate: '2026-07-21', eventTime: '08:00' }),
      createEvent({ id: 6, nameMassOrEvent: 'Evento D', eventDate: '2026-07-30', eventTime: '15:00:00' }),
      createEvent({ id: 2, nameMassOrEvent: 'Evento no momento atual', eventDate: '2026-07-20', eventTime: '10:00' }),
      createEvent({ id: 5, nameMassOrEvent: 'Evento B', eventDate: '2026-07-21', eventTime: '20:00' }),
      createEvent({ id: 3, nameMassOrEvent: 'Evento C', eventDate: '2026-07-25', eventTime: '09:00' }),
    ];

    await setup(false, of(events));

    const ids = fixture.componentInstance.upcomingEvents().map((event) => event.id);

    expect(ids).toEqual([2, 4, 5, 3, 6]);
    expect(ids.length).toBe(5);
  });

  it('should not mutate the original events collection returned by the service', async () => {
    const events = [
      createEvent({ id: 7, eventDate: '2026-08-01', eventTime: '07:30' }),
      createEvent({ id: 4, eventDate: '2026-07-21', eventTime: '08:00' }),
      createEvent({ id: 6, eventDate: '2026-07-30', eventTime: '15:00' }),
    ];
    const originalOrder = events.map((event) => event.id);

    await setup(false, of(events));

    expect(events.map((event) => event.id)).toEqual(originalOrder);
  });

  it('should render Missa and Celebração labels correctly', async () => {
    const mass = createEvent({
      id: 1,
      eventDate: '2026-07-21',
      eventTime: '08:00',
      massOrCelebration: true,
    });
    const celebration = createEvent({
      id: 2,
      eventDate: '2026-07-22',
      eventTime: '08:00',
      massOrCelebration: false,
    });

    await setup(false, of([mass, celebration]));

    const text = textContent();

    expect(text).toContain('Missa');
    expect(text).toContain('Celebração');
  });

  it('should render a details link for each upcoming event pointing to the event route', async () => {
    const event = createEvent({ id: 42, eventDate: '2026-07-21', eventTime: '08:00' });

    await setup(false, of([event]));

    const detailLink = eventLinks().find((link) => link.textContent?.includes('Ver evento'));

    expect(detailLink).toBeDefined();
    expect(detailLink?.getAttribute('href')).toBe('/app/eventos/42');
  });

  it('should show the empty state message while keeping quick access available', async () => {
    await setup(false, of([]));

    const text = textContent();

    expect(text).toContain('Nenhum próximo evento foi encontrado.');
    expect(text).toContain('Eventos');
    expect(text).toContain('Escalas');
    expect(text).toContain('Locais');
  });

  it('should show an error message when loading events fails, without hiding quick access', async () => {
    await setup(false, throwError(() => new HttpErrorResponse({ status: 500 })));

    const text = textContent();

    expect(text).toContain('Não foi possível carregar os próximos eventos.');
    expect(text).toContain('Tentar novamente');
    expect(text).toContain('Eventos');
    expect(text).toContain('Escalas');
    expect(text).toContain('Locais');
  });

  it('should reload events when the retry button is clicked', async () => {
    await setup(false, throwError(() => new HttpErrorResponse({ status: 500 })));
    eventService.findAll.and.returnValue(
      of([
        createEvent({
          id: 3,
          nameMassOrEvent: 'Missa da Tarde',
          eventDate: '2026-07-21',
          eventTime: '08:00',
        }),
      ]),
    );

    const retryButton = fixture.nativeElement.querySelector('.home__button') as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Missa da Tarde');
    expect(textContent()).not.toContain('Não foi possível carregar os próximos eventos.');
  });

  it('should prevent a duplicate request while a retry is already loading', async () => {
    await setup();
    const pending = new Subject<CelebrationEventResponse[]>();
    eventService.findAll.and.returnValue(pending);

    fixture.componentInstance.loadEvents();
    fixture.componentInstance.loadEvents();

    expect(eventService.findAll).toHaveBeenCalledTimes(2);

    pending.next([]);
    pending.complete();
  });

  it('should render the five quick access links as real, unique anchors', async () => {
    await setup();

    const anchors = quickAccessAnchors();
    const labels = anchors.map((anchor) => anchor.querySelector('span')?.textContent?.trim());
    const targets = anchors.map((anchor) => anchor.getAttribute('href'));

    expect(anchors.length).toBe(5);
    anchors.forEach((anchor) => expect(anchor.tagName).toBe('A'));
    expect(labels).toEqual(['Eventos', 'Minhas escalas', 'Escalas', 'Pessoas', 'Locais']);
    expect(labels.filter((label) => label === 'Pessoas').length).toBe(1);
    expect(targets).toContain('/app/eventos');
    expect(targets).toContain('/app/minhas-escalas');
    expect(targets).toContain('/app/escalas');
    expect(targets).toContain('/app/locais');
  });

  it('should show the "Minhas escalas" quick access pointing to the authenticated route for operators', async () => {
    await setup(false);

    const operatorCard = quickAccessCardByLabel('Minhas escalas');

    expect(operatorCard.getAttribute('href')).toBe('/app/minhas-escalas');
    expect(operatorCard.textContent).toContain(
      'Consulte os eventos em que você está escalado.',
    );
  });

  it('should show the "Minhas escalas" quick access pointing to the authenticated route for administrators', async () => {
    await setup(true);

    const adminCard = quickAccessCardByLabel('Minhas escalas');

    expect(adminCard.getAttribute('href')).toBe('/app/minhas-escalas');
  });

  it('should point Pessoas to the admin directory and show the admin description for administrators', async () => {
    await setup(true);

    const pessoasCard = quickAccessCardByLabel('Pessoas');

    expect(pessoasCard.getAttribute('href')).toBe('/app/admin/usuarios');
    expect(pessoasCard.textContent).toContain('Gerencie pessoas, ministérios e perfis de acesso.');
  });

  it('should point Pessoas to the public directory and show the operator description for operators', async () => {
    await setup(false);

    const pessoasCard = quickAccessCardByLabel('Pessoas');

    expect(pessoasCard.getAttribute('href')).toBe('/app/pessoas');
    expect(pessoasCard.textContent).toContain('Consulte as categorias ministeriais cadastradas.');
  });

  it('should render the administrative actions section for ROLE_ADMIN', async () => {
    await setup(true);

    const text = textContent();
    const adminTargets = adminAnchors().map((link) => link.getAttribute('href'));

    expect(text).toContain('Administração');
    expect(text).toContain('Gerenciar eventos');
    expect(text).toContain('Novo evento com escala');
    expect(text).toContain('Pessoas e acessos');
    expect(text).toContain('Gerenciar locais');
    expect(adminTargets).toEqual([
      '/app/admin/eventos',
      '/app/admin/escalas/novo-evento',
      '/app/admin/usuarios',
      '/app/admin/locais',
    ]);
  });

  it('should not render any administrative action for ROLE_OPERATOR', async () => {
    await setup(false);

    const text = textContent();
    const allTargets = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('a'),
    ).map((link) => link.getAttribute('href'));

    expect((fixture.nativeElement as HTMLElement).querySelector('#admin-actions-title')).toBeNull();
    expect(adminAnchors().length).toBe(0);
    expect(text).not.toContain('Administração');
    expect(text).not.toContain('Gerenciar eventos');
    expect(text).not.toContain('Novo evento com escala');
    expect(text).not.toContain('Gerenciar locais');
    expect(allTargets).not.toContain('/app/admin/eventos');
    expect(allTargets).not.toContain('/app/admin/escalas/novo-evento');
    expect(allTargets).not.toContain('/app/admin/locais');
  });

  it('should not render event location or fictitious metrics', async () => {
    const event = createEvent({ id: 1, eventDate: '2026-07-21', eventTime: '08:00' });

    await setup(false, of([event]));

    const text = textContent();

    expect(text).not.toContain('Igreja');
    expect(text).not.toContain('Local');
    expect(text).not.toContain('escalados');
    expect(text).not.toContain('%');
    expect(text).not.toContain('grafico');
    expect(text).not.toContain('missas este mes');
    expect(text).not.toContain('total de pessoas');
  });

  describe('Próximas escalas', () => {
    it('should request user schedules starting from the current local date', async () => {
      await setup();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ startDate: '2026-07-20' }),
      );
    });

    it('should request user schedules ending 90 days after the current local date', async () => {
      await setup();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ endDate: '2026-10-18' }),
      );
    });

    it('should always request page 0', async () => {
      await setup();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ page: 0 }),
      );
    });

    it('should always request size 20', async () => {
      await setup();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ size: 20 }),
      );
    });

    it('should call the user schedules endpoint only once on initialization', async () => {
      await setup();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(1);
    });

    it('should show a loading state while user schedules are pending', async () => {
      await setup();
      const pending = new Subject<CurrentUserSchedulePage>();
      currentUserScheduleService.findSchedules.and.returnValue(pending);

      fixture.componentInstance.loadUserSchedules();
      fixture.detectChanges();

      expect(fixture.componentInstance.isLoadingUserSchedules()).toBeTrue();
      expect(textContent()).toContain('Carregando suas próximas escalas...');

      pending.next(createSchedulePage({ content: [] }));
      pending.complete();
    });

    it('should show an error message when loading user schedules fails', async () => {
      await setup(false, of([createEvent()]), NOW, {}, throwError(() => new HttpErrorResponse({ status: 500 })));

      const text = textContent();

      expect(text).toContain('Não foi possível carregar suas próximas escalas.');
      expect(text).toContain('Tentar novamente');
    });

    it('should reload user schedules when the retry button is clicked', async () => {
      await setup(false, of([createEvent()]), NOW, {}, throwError(() => new HttpErrorResponse({ status: 500 })));
      currentUserScheduleService.findSchedules.and.returnValue(
        of(createSchedulePage({ content: [createSchedule({ eventName: 'Missa da Tarde' })] })),
      );

      const retryButtons = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('.home__button'),
      ) as HTMLButtonElement[];
      retryButtons[0].click();
      fixture.detectChanges();

      expect(currentUserScheduleService.findSchedules).toHaveBeenCalledTimes(2);
      expect(textContent()).toContain('Missa da Tarde');
      expect(textContent()).not.toContain('Não foi possível carregar suas próximas escalas.');
    });

    it('should not reload the general upcoming events when retrying user schedules', async () => {
      await setup(false, of([createEvent()]), NOW, {}, throwError(() => new HttpErrorResponse({ status: 500 })));
      currentUserScheduleService.findSchedules.and.returnValue(of(createSchedulePage({ content: [] })));

      const retryButtons = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('.home__button'),
      ) as HTMLButtonElement[];
      retryButtons[0].click();
      fixture.detectChanges();

      expect(eventService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should show the empty state message when there are no user schedules in the next 90 days', async () => {
      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [] })));

      expect(textContent()).toContain('Você não possui escalas nos próximos 90 dias.');
    });

    it('should limit the user schedules list to a maximum of five cards', async () => {
      const schedules = Array.from({ length: 7 }, (_, index) =>
        createSchedule({
          eventId: index + 1,
          eventDate: '2026-07-21',
          eventTime: `0${(index % 9) + 1}:00`,
        }),
      );

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: schedules })));

      expect(fixture.componentInstance.upcomingUserSchedules().length).toBe(5);
    });

    it('should remove a user schedule whose date and time have already passed', async () => {
      const past = createSchedule({ eventId: 1, eventDate: '2026-07-19', eventTime: '23:59:59' });
      const future = createSchedule({ eventId: 2, eventDate: '2026-07-21', eventTime: '08:00' });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [past, future] })));

      expect(fixture.componentInstance.upcomingUserSchedules().map((schedule) => schedule.eventId)).toEqual([2]);
    });

    it('should keep a user schedule happening later on the current day', async () => {
      const laterToday = createSchedule({ eventId: 3, eventDate: '2026-07-20', eventTime: '18:00' });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [laterToday] })));

      expect(fixture.componentInstance.upcomingUserSchedules().map((schedule) => schedule.eventId)).toEqual([3]);
    });

    it('should keep a user schedule happening on a future day', async () => {
      const nextWeek = createSchedule({ eventId: 4, eventDate: '2026-07-27', eventTime: '09:00' });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [nextWeek] })));

      expect(fixture.componentInstance.upcomingUserSchedules().map((schedule) => schedule.eventId)).toEqual([4]);
    });

    it('should display the date and time of each user schedule', async () => {
      const schedule = createSchedule({ eventDate: '2026-07-21', eventTime: '19:00:00' });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [schedule] })));

      const text = textContent();

      expect(text).toContain('21/07/2026');
      expect(text).toContain('19:00');
    });

    it('should display the location name when present', async () => {
      const schedule = createSchedule({ eventDate: '2026-07-21', locationName: 'Igreja Matriz' });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [schedule] })));

      expect(textContent()).toContain('Igreja Matriz');
    });

    it('should show a fallback message when the location is null', async () => {
      const schedule = createSchedule({
        eventDate: '2026-07-21',
        locationId: null,
        locationName: null,
      });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [schedule] })));

      expect(textContent()).toContain('Local não informado.');
    });

    it('should label mass and celebration user schedules correctly', async () => {
      const mass = createSchedule({ eventId: 1, eventDate: '2026-07-21', massOrCelebration: true });
      const celebration = createSchedule({ eventId: 2, eventDate: '2026-07-22', massOrCelebration: false });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [mass, celebration] })));

      const text = textContent();

      expect(text).toContain('Missa');
      expect(text).toContain('Celebração');
    });

    it('should translate all five assignment types', async () => {
      const schedule = createSchedule({
        eventDate: '2026-07-21',
        assignments: ['PRIEST', 'READER', 'COMMENTATOR', 'MINISTER_OF_THE_WORD', 'EUCHARISTIC_MINISTER'],
      });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [schedule] })));

      const text = textContent();

      expect(text).toContain('Padre');
      expect(text).toContain('Leitor');
      expect(text).toContain('Comentarista');
      expect(text).toContain('Ministro da Palavra');
      expect(text).toContain('Ministro da Eucaristia');
    });

    it('should render two assignments within the same card', async () => {
      const schedule = createSchedule({ eventDate: '2026-07-21', assignments: ['READER', 'COMMENTATOR'] });

      await setup(false, of([createEvent()]), NOW, {}, of(createSchedulePage({ content: [schedule] })));

      const card = userScheduleCards()[0];

      expect(card.textContent).toContain('Leitor');
      expect(card.textContent).toContain('Comentarista');
    });

    it('should render the "Ver todas as minhas escalas" link', async () => {
      await setup();

      const link = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('a'),
      ).find((anchor) => anchor.textContent?.trim() === 'Ver todas as minhas escalas') as
        | HTMLAnchorElement
        | undefined;

      expect(link).toBeDefined();
    });

    it('should point the "Ver todas as minhas escalas" link to /app/minhas-escalas', async () => {
      await setup();

      const link = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('a'),
      ).find((anchor) => anchor.textContent?.trim() === 'Ver todas as minhas escalas') as HTMLAnchorElement;

      expect(link.getAttribute('href')).toBe('/app/minhas-escalas');
    });

    it('should preserve the general upcoming events section unchanged', async () => {
      const event = createEvent({ id: 42, nameMassOrEvent: 'Missa Dominical' });

      await setup(false, of([event]));

      const text = textContent();

      expect(text).toContain('Próximos eventos');
      expect(text).toContain('Missa Dominical');
      expect(eventService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should preserve quick access links and administrative actions', async () => {
      await setup(true);

      const labels = quickAccessAnchors().map((anchor) => anchor.querySelector('span')?.textContent?.trim());

      expect(labels).toEqual(['Eventos', 'Minhas escalas', 'Escalas', 'Pessoas', 'Locais']);
      expect(textContent()).toContain('Administração');
      expect(adminAnchors().length).toBe(4);
    });
  });

  it('should expose a single h1 and accessible loading, error and result states', async () => {
    await setup();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('h1').length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).querySelector('[aria-live="polite"]')).not.toBeNull();

    eventService.findAll.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.loadEvents();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector('.home__button') as HTMLButtonElement;

    expect(retryButton).not.toBeNull();
    expect(retryButton.getAttribute('type')).toBe('button');
    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
  });

  function quickAccessAnchors(): HTMLAnchorElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.home__card:not(.home__card--admin)'),
    );
  }

  function quickAccessCardByLabel(label: string): HTMLAnchorElement {
    const anchor = quickAccessAnchors().find(
      (element) => element.querySelector('span')?.textContent?.trim() === label,
    );

    expect(anchor).toBeDefined();

    return anchor as HTMLAnchorElement;
  }

  function adminAnchors(): HTMLAnchorElement[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.home__card--admin'));
  }

  function eventLinks(): HTMLAnchorElement[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.home__event-link'));
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function createEvent(overrides: Partial<CelebrationEventResponse> = {}): CelebrationEventResponse {
    return {
      id: 1,
      nameMassOrEvent: 'Missa Dominical',
      eventDate: '2026-07-25',
      eventTime: '09:00:00',
      massOrCelebration: true,
      ...overrides,
    };
  }

  function createProfile(overrides: Partial<CurrentUserProfile> = {}): CurrentUserProfile {
    return {
      id: 10,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      birthdayDate: '1990-01-01',
      roles: ['ROLE_OPERATOR'],
      ministries: ['READER'],
      ...overrides,
    };
  }

  function userScheduleCards(): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll(
        '#upcoming-user-schedules-title ~ div .home__event-card',
      ),
    );
  }

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

  function createSchedulePage(overrides: Partial<CurrentUserSchedulePage> = {}): CurrentUserSchedulePage {
    return {
      content: [createSchedule()],
      totalPages: 1,
      totalElements: 1,
      first: true,
      last: true,
      size: 20,
      number: 0,
      numberOfElements: 1,
      empty: false,
      ...overrides,
    };
  }
});
