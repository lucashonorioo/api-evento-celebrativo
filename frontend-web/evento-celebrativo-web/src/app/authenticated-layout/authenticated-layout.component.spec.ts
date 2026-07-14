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

  it('should render a navigation link to the authenticated home route', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('.app-navigation__link') as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/app/inicio');
    expect(link?.textContent).toContain('Inicio');
  });

  it('should render the router outlet for child pages', async () => {
    await setup();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('router-outlet')).not.toBeNull();
  });
});
