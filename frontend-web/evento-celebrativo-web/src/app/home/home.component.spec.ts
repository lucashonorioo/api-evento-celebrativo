import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { HomeComponent } from './home.component';

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;

  async function setup(username: string | null = '11999999999'): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', ['getUsername']);
    authSessionService.getUsername.and.returnValue(username);

    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the main title and greeting', async () => {
    await setup('11999999999');

    const text = textContent();

    expect(text).toContain('Painel da Paroquia');
    expect(text).toContain('Bem-vindo, 11999999999');
  });

  it('should render real quick access links', async () => {
    await setup();

    const linkTargets = links().map((link) => link.getAttribute('href'));
    const text = textContent();

    expect(text).toContain('Consultar eventos');
    expect(text).toContain('Consultar escala');
    expect(text).toContain('Pessoas');
    expect(text).toContain('Locais');
    expect(linkTargets).toContain('/app/eventos');
    expect(linkTargets).toContain('/app/escala/eucaristia');
    expect(linkTargets).toContain('/app/pessoas');
    expect(linkTargets).toContain('/app/locais');
  });

  it('should not render administrative actions', async () => {
    await setup();

    expect(textContent()).not.toContain('Gerenciar locais');
    expect(textContent()).not.toContain('Cadastrar');
    expect(textContent()).not.toContain('Editar');
    expect(textContent()).not.toContain('Excluir');
    expect(links().map((link) => link.getAttribute('href'))).not.toContain('/app/admin/locais');
  });

  it('should not render fictitious metrics', async () => {
    await setup();

    const text = textContent();

    expect(text).not.toContain('%');
    expect(text).not.toContain('grafico');
    expect(text).not.toContain('missas este mes');
    expect(text).not.toContain('total de pessoas');
  });

  function links(): HTMLAnchorElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('a'));
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
