import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';
import { AuthenticatedLayoutComponent } from './authenticated-layout.component';

describe('AuthenticatedLayoutComponent', () => {
  let fixture: ComponentFixture<AuthenticatedLayoutComponent>;
  let authService: jasmine.SpyObj<AuthService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;

  async function setup(username: string | null = '11999999999'): Promise<void> {
    authService = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
    ]);
    authSessionService.getUsername.and.returnValue(username);

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
    expect(linkTargets).toContain('/app/escala/eucaristia');
    expect(linkTexts.join(' ')).toContain('Inicio');
    expect(linkTexts.join(' ')).toContain('Eventos');
    expect(linkTexts.join(' ')).toContain('Escala de Eucaristia');
  });

  it('should render the router outlet for child pages', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
