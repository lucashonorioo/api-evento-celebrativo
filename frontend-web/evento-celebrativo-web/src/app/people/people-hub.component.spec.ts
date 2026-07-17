import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthSessionService } from '../auth-session.service';
import { PeopleHubComponent } from './people-hub.component';

describe('PeopleHubComponent', () => {
  let fixture: ComponentFixture<PeopleHubComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;

  async function setup(isAdmin = false): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);

    await TestBed.configureTestingModule({
      imports: [PeopleHubComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PeopleHubComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title', async () => {
    await setup();

    expect(textContent()).toContain('Central de pessoas');
  });

  it('should link to each person category', async () => {
    await setup();

    const linkTargets = links().map((link) => link.getAttribute('href'));

    expect(linkTargets).toContain('/app/leitores');
    expect(linkTargets).toContain('/app/comentaristas');
    expect(linkTargets).toContain('/app/padres');
    expect(linkTargets).toContain('/app/ministros-palavra');
    expect(linkTargets).toContain('/app/ministros-eucaristia');
  });

  it('should render the user management card for administrators', async () => {
    await setup(true);

    const text = textContent();
    const linkTargets = links().map((link) => link.getAttribute('href'));

    expect(text).toContain('Usuários');
    expect(text).toContain('Gerencie os perfis de acesso das pessoas cadastradas.');
    expect(linkTargets).toContain('/app/admin/usuarios');
    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
  });

  it('should not render the user management card for operators', async () => {
    await setup(false);

    const text = textContent();
    const linkTargets = links().map((link) => link.getAttribute('href'));

    expect(text).not.toContain('Usuários');
    expect(linkTargets).not.toContain('/app/admin/usuarios');
  });

  it('should not render unsupported administrative actions for operators', async () => {
    await setup(false);

    const text = textContent();

    expect(text).not.toContain('Cadastrar');
    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Excluir');
    expect(text).not.toContain('Gerenciar');
  });

  it('should not expose personal data', async () => {
    await setup();

    const text = textContent();

    expect(text).not.toContain('telefone');
    expect(text).not.toContain('birthdayDate');
    expect(text).not.toContain('phoneNumber');
    expect(text).not.toContain('access_token');
  });

  function links(): HTMLAnchorElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('a'));
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});
