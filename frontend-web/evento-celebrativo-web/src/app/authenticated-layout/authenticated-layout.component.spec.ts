import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';
import { AuthenticatedLayoutComponent } from './authenticated-layout.component';

describe('AuthenticatedLayoutComponent', () => {
  let fixture: ComponentFixture<AuthenticatedLayoutComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;

  async function setup(username: string | null = '11999999999', isAdmin = false): Promise<void> {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
      'hasAuthority',
    ]);
    authSessionService.getUsername.and.returnValue(username);
    authSessionService.hasAuthority.and.returnValue(isAdmin);

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayoutComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: AuthSessionService, useValue: authSessionService },
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

  it('should render the username and initials in the topbar', async () => {
    await setup('Maria Silva');

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.user-menu__name')?.textContent).toContain('Maria Silva');
    expect(compiled.querySelector('.user-menu__avatar')?.textContent).toContain('MS');
  });

  it('should render safe fallback data when username is not available', async () => {
    await setup(null);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.user-menu__name')?.textContent).toContain('Usuario');
    expect(compiled.querySelector('.user-menu__avatar')?.textContent).toContain('US');
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

  it('should request logout from the user menu and close it', async () => {
    await setup();

    userMenuTrigger().click();
    fixture.detectChanges();

    const logoutButton = fixture.nativeElement.querySelector(
      '.user-menu__item',
    ) as HTMLButtonElement;
    logoutButton.click();
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
      '/app/escalas',
      '/app/pessoas',
      '/app/locais',
    ]);
    expect(sidebarText).toContain('Inicio');
    expect(sidebarText).toContain('Eventos');
    expect(sidebarText).toContain('Escalas');
    expect(sidebarText).toContain('Pessoas');
    expect(sidebarText).toContain('Locais');
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

  it('should keep the same main sidebar links for administrators', async () => {
    await setup('11999999999', true);

    const linkTargets = sidebarLinks().map((link) => link.getAttribute('href'));
    const sidebarText = sidebar().textContent ?? '';

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(linkTargets).toEqual([
      '/app/inicio',
      '/app/eventos',
      '/app/escalas',
      '/app/pessoas',
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

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
