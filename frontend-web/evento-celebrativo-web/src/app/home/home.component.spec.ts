import { HttpErrorResponse } from '@angular/common/http';
import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../auth-session.service';
import { CurrentUserProfile } from '../current-user-profile/current-user-profile.models';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';
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

  async function setup(
    isAdmin = false,
    events$: Observable<CelebrationEventResponse[]> = of([createEvent()]),
    now: Date = NOW,
    profileOptions: {
      profile?: CurrentUserProfile | null;
      isLoading?: boolean;
      errorMessage?: string | null;
    } = {},
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

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventService, useValue: eventService },
        { provide: CurrentUserProfileService, useValue: currentUserProfileService },
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

  it('should render the four quick access links as real, unique anchors', async () => {
    await setup();

    const anchors = quickAccessAnchors();
    const labels = anchors.map((anchor) => anchor.querySelector('span')?.textContent?.trim());
    const targets = anchors.map((anchor) => anchor.getAttribute('href'));

    expect(anchors.length).toBe(4);
    anchors.forEach((anchor) => expect(anchor.tagName).toBe('A'));
    expect(labels).toEqual(['Eventos', 'Escalas', 'Pessoas', 'Locais']);
    expect(labels.filter((label) => label === 'Pessoas').length).toBe(1);
    expect(targets).toContain('/app/eventos');
    expect(targets).toContain('/app/escalas');
    expect(targets).toContain('/app/locais');
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
});
