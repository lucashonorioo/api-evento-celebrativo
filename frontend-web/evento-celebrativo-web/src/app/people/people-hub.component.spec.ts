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

  it('should link to each of the five ministerial categories only', async () => {
    await setup();

    const linkTargets = links().map((link) => link.getAttribute('href'));
    const cards = fixture.nativeElement.querySelectorAll('.people-hub__card');

    expect(linkTargets).toContain('/app/leitores');
    expect(linkTargets).toContain('/app/comentaristas');
    expect(linkTargets).toContain('/app/padres');
    expect(linkTargets).toContain('/app/ministros-palavra');
    expect(linkTargets).toContain('/app/ministros-eucaristia');
    expect(cards.length).toBe(5);
  });

  it('should not render the redundant people and access directory card for administrators', async () => {
    await setup(true);

    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.people-hub__card'),
    ) as HTMLElement[];
    const cardTitles = cards.map((card) => card.querySelector('h2')?.textContent?.trim());
    const linkTargets = links().map((link) => link.getAttribute('href'));

    expect(cardTitles).not.toContain('Pessoas e acessos');
    expect(cards.length).toBe(5);
    expect(linkTargets.filter((href) => href === '/app/admin/usuarios').length).toBe(1);
  });

  it('should not render the redundant people and access directory card for operators', async () => {
    await setup(false);

    const cards = Array.from(
      fixture.nativeElement.querySelectorAll('.people-hub__card'),
    ) as HTMLElement[];
    const cardTitles = cards.map((card) => card.querySelector('h2')?.textContent?.trim());

    expect(cardTitles).not.toContain('Pessoas e acessos');
    expect(cards.length).toBe(5);
  });

  it('should show "Voltar para Pessoas e acessos" for administrators, pointing to the admin directory', async () => {
    await setup(true);

    const backLink = (
      Array.from(fixture.nativeElement.querySelectorAll('a')) as HTMLAnchorElement[]
    ).find((link) => link.textContent?.trim() === 'Voltar para Pessoas e acessos');

    expect(backLink).toBeDefined();
    expect(backLink?.getAttribute('href')).toBe('/app/admin/usuarios');
    expect(authSessionService.hasAuthority).toHaveBeenCalledOnceWith('ROLE_ADMIN');
  });

  it('should not show "Voltar para Pessoas e acessos" for operators', async () => {
    await setup(false);

    expect(textContent()).not.toContain('Voltar para Pessoas e acessos');
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
