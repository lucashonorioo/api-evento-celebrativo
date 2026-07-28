import { WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, RouterLinkActive } from '@angular/router';
import { By } from '@angular/platform-browser';

import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';
import { CurrentUserProfile } from '../current-user-profile/current-user-profile.models';
import { CurrentUserProfileService } from '../current-user-profile/current-user-profile.service';
import { AuthenticatedLayoutComponent } from './authenticated-layout.component';

interface CurrentUserProfileServiceDouble {
  readonly profile: WritableSignal<CurrentUserProfile | null>;
  readonly isLoading: WritableSignal<boolean>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly loadProfile: jasmine.Spy;
  readonly updateProfile: jasmine.Spy;
  readonly clearProfile: jasmine.Spy;
}

interface ProfileSetupOptions {
  readonly profile?: CurrentUserProfile | null;
  readonly isLoading?: boolean;
  readonly errorMessage?: string | null;
  readonly jwtUsername?: string | null;
}

describe('AuthenticatedLayoutComponent', () => {
  let fixture: ComponentFixture<AuthenticatedLayoutComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let currentUserProfileService: CurrentUserProfileServiceDouble;

  async function setup(isAdmin = false, options: ProfileSetupOptions = {}): Promise<void> {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
      'hasAuthority',
    ]);
    authSessionService.getUsername.and.returnValue(options.jwtUsername ?? '34999999999');
    authSessionService.hasAuthority.and.returnValue(isAdmin);

    currentUserProfileService = {
      profile: signal(options.profile === undefined ? createProfile() : options.profile),
      isLoading: signal(options.isLoading ?? false),
      errorMessage: signal(options.errorMessage ?? null),
      loadProfile: jasmine.createSpy('loadProfile'),
      updateProfile: jasmine.createSpy('updateProfile'),
      clearProfile: jasmine.createSpy('clearProfile'),
    };

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayoutComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: CurrentUserProfileService, useValue: currentUserProfileService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the system brand', async () => {
    await setup();

    const text = textContent();

    expect(text).toContain('Evento');
    expect(text).toContain('Celebrativo');
  });

  it('should load the profile on initialization', async () => {
    await setup();

    expect(currentUserProfileService.loadProfile).toHaveBeenCalledTimes(1);
  });

  it('should render the name coming from the profile', async () => {
    await setup(false, { profile: createProfile({ name: 'João da Silva' }) });

    expect(userMenuName()?.textContent).toContain('João da Silva');
  });

  it('should not render the JWT-derived phone number as the display name', async () => {
    await setup(false, {
      profile: createProfile({ name: 'João da Silva' }),
      jwtUsername: '34999999999',
    });

    const nameText = userMenuName()?.textContent ?? '';

    expect(nameText).not.toContain('34999999999');
    expect(nameText).toContain('João da Silva');
  });

  it('should render only the first initial in the avatar for a multi-word name', async () => {
    await setup(false, { profile: createProfile({ name: 'João da Silva' }) });

    expect(userMenuAvatar()?.textContent?.trim()).toBe('J');
  });

  it('should render a single initial for a one-word name', async () => {
    await setup(false, { profile: createProfile({ name: 'Maria' }) });

    expect(userMenuAvatar()?.textContent?.trim()).toBe('M');
  });

  it('should trim surrounding spaces from the profile name for display and initial', async () => {
    await setup(false, { profile: createProfile({ name: '  João da Silva  ' }) });

    expect(userMenuName()?.textContent?.trim()).toBe('João da Silva');
    expect(userMenuAvatar()?.textContent?.trim()).toBe('J');
  });

  it('should show a neutral loading state before the profile arrives', async () => {
    await setup(false, { profile: null, isLoading: true });

    expect(userMenuName()?.textContent).toContain('Carregando perfil...');
    expect(userMenuAvatar()?.textContent?.trim()).toBe('?');
  });

  it('should show a neutral error state when the profile fails to load', async () => {
    await setup(false, {
      profile: null,
      isLoading: false,
      errorMessage: 'Não foi possível carregar o seu perfil. Tente novamente.',
    });

    expect(userMenuName()?.textContent).toContain('Perfil indisponível');
    expect(userMenuAvatar()?.textContent?.trim()).toBe('?');
  });

  it('should open and close the user menu', async () => {
    await setup();

    const trigger = fixture.nativeElement.querySelector(
      '.user-menu__trigger',
    ) as HTMLButtonElement;

    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('.user-menu__panel')).toBeNull();

    trigger.click();
    fixture.detectChanges();

    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.user-menu__panel')).not.toBeNull();

    trigger.click();
    fixture.detectChanges();

    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('.user-menu__panel')).toBeNull();
  });

  it('should render a real "Meu perfil" link pointing to /app/perfil', async () => {
    await setup();

    userMenuTrigger().click();
    fixture.detectChanges();

    const link = myProfileLink();

    expect(link.tagName).toBe('A');
    expect(link.textContent).toContain('Meu perfil');
    expect(link.getAttribute('href')).toBe('/app/perfil');
    expect(link.getAttribute('role')).toBe('menuitem');
  });

  it('should close the user menu after clicking Meu perfil', async () => {
    await setup();

    userMenuTrigger().click();
    fixture.detectChanges();

    myProfileLink().click();
    fixture.detectChanges();

    expect(fixture.componentInstance.isUserMenuOpen()).toBeFalse();
    expect(fixture.nativeElement.querySelector('.user-menu__panel')).toBeNull();
  });

  it('should request logout from the user menu and close it', async () => {
    await setup();

    userMenuTrigger().click();
    fixture.detectChanges();

    const logoutButton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.user-menu__item'),
    ).find((element): element is HTMLButtonElement => element.textContent?.trim() === 'Sair') as
      | HTMLButtonElement
      | undefined;
    logoutButton?.click();
    fixture.detectChanges();

    expect(authService.logout).toHaveBeenCalledOnceWith();
    expect(fixture.componentInstance.isUserMenuOpen()).toBeFalse();
  });

  it('should render only the main sidebar links', async () => {
    await setup();

    const linkTargets = sidebarLinks().map((link) => link.getAttribute('href'));
    const sidebarText = sidebar().textContent ?? '';

    expect(linkTargets).toEqual([
      '/app/inicio',
      '/app/eventos',
      '/app/minhas-escalas',
      '/app/escalas',
      '/app/pessoas',
      '/app/locais',
    ]);
    expect(sidebarText).toContain('Inicio');
    expect(sidebarText).toContain('Eventos');
    expect(sidebarText).toContain('Minhas escalas');
    expect(sidebarText).toContain('Escalas');
    expect(sidebarText).toContain('Pessoas');
    expect(sidebarText).toContain('Locais');
  });

  it('should render "Minhas escalas" before "Escalas" for operators', async () => {
    await setup(false);

    const targets = sidebarLinks().map((link) => link.getAttribute('href'));
    const myScheduleIndex = targets.indexOf('/app/minhas-escalas');
    const scheduleIndex = targets.indexOf('/app/escalas');

    expect(myScheduleIndex).toBeGreaterThan(-1);
    expect(myScheduleIndex).toBeLessThan(scheduleIndex);
  });

  it('should render "Minhas escalas" before "Escalas" for administrators', async () => {
    await setup(true);

    const targets = sidebarLinks().map((link) => link.getAttribute('href'));
    const myScheduleIndex = targets.indexOf('/app/minhas-escalas');
    const scheduleIndex = targets.indexOf('/app/escalas');

    expect(myScheduleIndex).toBeGreaterThan(-1);
    expect(myScheduleIndex).toBeLessThan(scheduleIndex);
  });

  it('should close the sidebar after clicking "Minhas escalas"', async () => {
    await setup();
    fixture.componentInstance.isSidebarOpen.set(true);
    fixture.detectChanges();

    const myScheduleLink = sidebarLinks().find(
      (link) => link.getAttribute('href') === '/app/minhas-escalas',
    ) as HTMLAnchorElement;
    myScheduleLink.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.isSidebarOpen()).toBeFalse();
  });

  it('should link Pessoas to the public people directory for operators', async () => {
    await setup(false);

    const peopleLinks = sidebarLinks().filter((link) => link.textContent?.trim() === 'Pessoas');

    expect(peopleLinks.length).toBe(1);
    expect(peopleLinks[0].getAttribute('href')).toBe('/app/pessoas');
  });

  it('should link Pessoas to the administrative people and access directory for administrators', async () => {
    await setup(true);

    const peopleLinks = sidebarLinks().filter((link) => link.textContent?.trim() === 'Pessoas');

    expect(peopleLinks.length).toBe(1);
    expect(peopleLinks[0].getAttribute('href')).toBe('/app/admin/usuarios');
  });

  it('should keep routerLinkActive configured on every sidebar link', async () => {
    await setup();

    const activeDirectives = fixture.debugElement.queryAll(By.directive(RouterLinkActive));

    expect(activeDirectives.length).toBe(sidebarLinks().length);
  });

  it('should not render individual person links in the sidebar', async () => {
    await setup();

    const sidebarText = sidebar().textContent ?? '';
    const linkTargets = sidebarLinks().map((link) => link.getAttribute('href'));

    expect(sidebarText).not.toContain('Leitores');
    expect(sidebarText).not.toContain('Comentaristas');
    expect(sidebarText).not.toContain('Padres');
    expect(sidebarText).not.toContain('Ministros da Palavra');
    expect(sidebarText).not.toContain('Ministros da Eucaristia');
    expect(linkTargets).not.toContain('/app/leitores');
    expect(linkTargets).not.toContain('/app/comentaristas');
    expect(linkTargets).not.toContain('/app/padres');
    expect(linkTargets).not.toContain('/app/ministros-palavra');
    expect(linkTargets).not.toContain('/app/ministros-eucaristia');
  });

  it('should not render unavailable navigation controls', async () => {
    await setup();

    const text = textContent();

    expect(text).not.toContain('Relatorios');
    expect(text).not.toContain('Configuracoes');
    expect(text).not.toContain('Buscar');
    expect(text).not.toContain('Notificacoes');
  });

  it('should open and close the sidebar with the mobile button', async () => {
    await setup();

    const button = fixture.nativeElement.querySelector(
      '.topbar__menu-button',
    ) as HTMLButtonElement;

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.componentInstance.isSidebarOpen()).toBeFalse();

    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.componentInstance.isSidebarOpen()).toBeTrue();

    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.componentInstance.isSidebarOpen()).toBeFalse();
  });

  it('should close the sidebar after navigating through a link', async () => {
    await setup();
    fixture.componentInstance.isSidebarOpen.set(true);
    fixture.detectChanges();

    sidebarLinks()[0].click();
    fixture.detectChanges();

    expect(fixture.componentInstance.isSidebarOpen()).toBeFalse();
  });

  it('should keep the same main sidebar items for administrators, with Pessoas pointing to the admin directory', async () => {
    await setup(true);

    const linkTargets = sidebarLinks().map((link) => link.getAttribute('href'));
    const sidebarText = sidebar().textContent ?? '';

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(linkTargets).toEqual([
      '/app/inicio',
      '/app/eventos',
      '/app/minhas-escalas',
      '/app/escalas',
      '/app/admin/usuarios',
      '/app/locais',
    ]);
    expect(sidebarText).not.toContain('Gerenciar locais');
  });

  it('should render the router outlet for child pages', async () => {
    await setup();

    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });

  function sidebar(): HTMLElement {
    return fixture.nativeElement.querySelector('.sidebar') as HTMLElement;
  }

  function sidebarLinks(): HTMLAnchorElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.sidebar__link'));
  }

  function userMenuTrigger(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('.user-menu__trigger') as HTMLButtonElement;
  }

  function userMenuName(): Element | null {
    return fixture.nativeElement.querySelector('.user-menu__name');
  }

  function userMenuAvatar(): Element | null {
    return fixture.nativeElement.querySelector('.user-menu__avatar');
  }

  function myProfileLink(): HTMLAnchorElement {
    return fixture.nativeElement.querySelector('.user-menu__item--link') as HTMLAnchorElement;
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
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
