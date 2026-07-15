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

  it('should render the username in the header', async () => {
    await setup('11999999999');

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.app-header__username')?.textContent).toContain('11999999999');
  });

  it('should render a safe fallback when username is not available', async () => {
    await setup(null);

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.app-header__username')?.textContent).toContain('Usuario');
  });

  it('should request logout when the logout button is clicked', async () => {
    await setup();
    const button = fixture.nativeElement.querySelector(
      '.app-header__logout',
    ) as HTMLButtonElement;

    button.click();

    expect(authService.logout).toHaveBeenCalledOnceWith();
  });

  it('should render navigation links to the authenticated routes', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll('.app-navigation__link'));
    const linkTargets = links.map((link) => link.getAttribute('href'));
    const linkTexts = links.map((link) => link.textContent);

    expect(linkTargets).toContain('/app/inicio');
    expect(linkTargets).toContain('/app/eventos');
    expect(linkTargets).toContain('/app/locais');
    expect(linkTargets).not.toContain('/app/admin/locais');
    expect(linkTargets).toContain('/app/leitores');
    expect(linkTargets).toContain('/app/comentaristas');
    expect(linkTargets).toContain('/app/padres');
    expect(linkTargets).toContain('/app/ministros-palavra');
    expect(linkTargets).toContain('/app/ministros-eucaristia');
    expect(linkTargets).toContain('/app/escala/eucaristia');
    expect(linkTexts.join(' ')).toContain('Inicio');
    expect(linkTexts.join(' ')).toContain('Eventos');
    expect(linkTexts.join(' ')).toContain('Locais');
    expect(linkTexts.join(' ')).not.toContain('Gerenciar locais');
    expect(linkTexts.join(' ')).toContain('Leitores');
    expect(linkTexts.join(' ')).toContain('Comentaristas');
    expect(linkTexts.join(' ')).toContain('Padres');
    expect(linkTexts.join(' ')).toContain('Ministros da Palavra');
    expect(linkTexts.join(' ')).toContain('Ministros da Eucaristia');
    expect(linkTexts.join(' ')).toContain('Escala de Eucaristia');
  });

  it('should render the location management link for administrators', async () => {
    await setup('11999999999', true);

    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll('.app-navigation__link'));
    const linkTargets = links.map((link) => link.getAttribute('href'));
    const linkTexts = links.map((link) => link.textContent);

    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
    expect(linkTargets).toContain('/app/admin/locais');
    expect(linkTexts.join(' ')).toContain('Gerenciar locais');
  });

  it('should keep the common locations link and hide management for operators', async () => {
    await setup('11999999999', false);

    const compiled = fixture.nativeElement as HTMLElement;
    const links = Array.from(compiled.querySelectorAll('.app-navigation__link'));
    const linkTargets = links.map((link) => link.getAttribute('href'));
    const linkTexts = links.map((link) => link.textContent);

    expect(linkTargets).toContain('/app/locais');
    expect(linkTargets).not.toContain('/app/admin/locais');
    expect(linkTexts.join(' ')).toContain('Locais');
    expect(linkTexts.join(' ')).not.toContain('Gerenciar locais');
    expect(linkTexts.join(' ')).toContain('Eventos');
    expect(linkTexts.join(' ')).toContain('Leitores');
  });

  it('should render the router outlet for child pages', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
