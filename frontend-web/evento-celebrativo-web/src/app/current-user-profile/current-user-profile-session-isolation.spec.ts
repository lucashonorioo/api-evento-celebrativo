import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { API_BASE_URL } from '../api.config';
import { AuthSessionService } from '../auth-session.service';
import { AuthService } from '../auth.service';
import { AuthenticatedLayoutComponent } from '../authenticated-layout/authenticated-layout.component';
import { CelebrationEventResponse } from '../events/event.models';
import { EventService } from '../events/event.service';
import { HomeComponent } from '../home/home.component';
import { CurrentUserProfile } from './current-user-profile.models';

describe('Current user profile session isolation', () => {
  let httpTestingController: HttpTestingController;

  beforeEach(async () => {
    const authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(false);

    const authService = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);

    const eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of([] as CelebrationEventResponse[]));

    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: AuthService, useValue: authService },
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('does not leak the previous session profile into a new session mounted without a page reload', () => {
    // 1. Perfil do usuário A é carregado na primeira montagem do layout autenticado.
    const layoutA = TestBed.createComponent(AuthenticatedLayoutComponent);
    layoutA.detectChanges();

    httpTestingController
      .expectOne(`${API_BASE_URL}/pessoas/me`)
      .flush(createProfile({ name: 'Usuário A' }));
    layoutA.detectChanges();

    expect(userMenuName(layoutA)).toContain('Usuário A');
    expect(userMenuAvatar(layoutA)).toBe('U');

    // 2. A sessão do usuário A é encerrada por 401 (o interceptor limpa apenas a sessão de
    // autenticação; o perfil central não é tocado nesse ponto) e 3. uma nova sessão é iniciada
    // para o usuário B, sem recarregar a aplicação.

    // 4. O AuthenticatedLayout é criado novamente para a nova sessão.
    const layoutB = TestBed.createComponent(AuthenticatedLayoutComponent);
    layoutB.detectChanges();

    // 5. O GET /pessoas/me do usuário B fica pendente: apenas fallbacks neutros devem aparecer,
    // em nenhum componente que compartilha o mesmo CurrentUserProfileService root.
    expect(userMenuName(layoutB)).not.toContain('Usuário A');
    expect(userMenuName(layoutB)).toContain('Carregando perfil...');
    expect(userMenuAvatar(layoutB)).toBe('?');

    const home = TestBed.createComponent(HomeComponent);
    home.detectChanges();

    expect(homeText(home)).not.toContain('Usuário A');
    expect(homeText(home)).toContain('Bem-vindo.');

    const pendingRequests = httpTestingController.match(`${API_BASE_URL}/pessoas/me`);
    expect(pendingRequests.length).toBe(1);

    // Se a nova carga falhar, o perfil antigo não deve voltar a aparecer em nenhum lugar.
    pendingRequests[0].flush({ error: 'Falha' }, { status: 500, statusText: 'Error' });
    layoutB.detectChanges();
    home.detectChanges();

    expect(userMenuName(layoutB)).not.toContain('Usuário A');
    expect(userMenuName(layoutB)).toContain('Perfil indisponível');
    expect(userMenuAvatar(layoutB)).toBe('?');
    expect(homeText(home)).not.toContain('Usuário A');
    expect(homeText(home)).toContain('Bem-vindo.');
  });

  function userMenuName(fixture: ComponentFixture<AuthenticatedLayoutComponent>): string {
    return (
      (fixture.nativeElement as HTMLElement).querySelector('.user-menu__name')?.textContent ?? ''
    );
  }

  function userMenuAvatar(fixture: ComponentFixture<AuthenticatedLayoutComponent>): string {
    return (
      (fixture.nativeElement as HTMLElement)
        .querySelector('.user-menu__avatar')
        ?.textContent?.trim() ?? ''
    );
  }

  function homeText(fixture: ComponentFixture<HomeComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function createProfile(overrides: Partial<CurrentUserProfile> = {}): CurrentUserProfile {
    return {
      id: 1,
      name: 'Usuário A',
      phoneNumber: '34999999999',
      birthdayDate: '1990-01-01',
      roles: ['ROLE_OPERATOR'],
      ministries: ['READER'],
      ...overrides,
    };
  }
});
