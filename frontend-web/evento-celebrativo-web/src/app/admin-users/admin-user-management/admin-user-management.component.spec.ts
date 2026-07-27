import { Location } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { PersonAdmin, PersonAdminPage, PersonMinistriesResponse } from '../admin-user.models';
import { AdminUserService } from '../admin-user.service';
import { AdminUserManagementComponent } from './admin-user-management.component';

describe('AdminUserManagementComponent', () => {
  let harness: RouterTestingHarness;
  let component: AdminUserManagementComponent;
  let adminUserService: jasmine.SpyObj<AdminUserService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let router: Router;
  let location: Location;
  let navigateSpy: jasmine.Spy;

  async function setup(
    page: PersonAdminPage = pageResponse(),
    username: string | null = '34000000000',
    url = '/admin/usuarios',
  ): Promise<void> {
    adminUserService = jasmine.createSpyObj<AdminUserService>('AdminUserService', [
      'findAll',
      'findById',
      'updateRole',
      'findMinistries',
      'updateMinistries',
    ]);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
    ]);
    adminUserService.findAll.and.returnValue(of(page));
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse()));
    authSessionService.getUsername.and.returnValue(username);

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin/usuarios', component: AdminUserManagementComponent }]),
        { provide: AdminUserService, useValue: adminUserService },
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    location = TestBed.inject(Location);
    navigateSpy = spyOn(router, 'navigate').and.callThrough();

    harness = await RouterTestingHarness.create(url);
    component = harness.routeDebugElement?.componentInstance as AdminUserManagementComponent;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should load the first page on init', async () => {
    await setup();

    expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
    expect(textContent()).toContain('Pessoas e acessos');
    expect(textContent()).toContain('Maria Silva');
    expect(textContent()).toContain('(34) 99999-9999');
    expect(textContent()).toContain('Leitor');
    expect(textContent()).toContain('Administrador');
  });

  it('should present itself as the people and access directory', async () => {
    await setup();

    const text = textContent();

    expect(text).toContain('Pessoas e acessos');
    expect(text).toContain('Gerencie os ministérios e os perfis de acesso das pessoas cadastradas.');
    expect(text).toContain('Pessoas cadastradas');
  });

  it('should render a secondary action linking to the ministerial categories hub', async () => {
    await setup();

    const action = Array.from(harness.routeNativeElement?.querySelectorAll('a') ?? []).find(
      (link) => link.textContent?.trim() === 'Consultar categorias ministeriais',
    ) as HTMLAnchorElement | undefined;

    expect(action).toBeDefined();
    expect(action?.getAttribute('href')).toBe('/app/pessoas');
  });

  it('should not add generic create, edit or delete person actions', async () => {
    await setup();

    const text = textContent();

    expect(text).not.toContain('Cadastrar pessoa');
    expect(text).not.toContain('Nova pessoa');
    expect(text).not.toContain('Editar pessoa');
    expect(text).not.toContain('Excluir pessoa');
  });

  it('should render empty states with and without filters', async () => {
    await setup(pageResponse({ content: [], totalElements: 0, totalPages: 0, empty: true }));

    expect(textContent()).toContain('Nenhuma pessoa cadastrada foi encontrada.');

    setInputValue('#user-name', 'Alice');
    submitFilters();

    expect(textContent()).toContain('Nenhuma pessoa foi encontrada com os filtros informados.');
  });

  it('should show an error and retry loading', async () => {
    adminUserService = jasmine.createSpyObj<AdminUserService>('AdminUserService', [
      'findAll',
      'findById',
      'updateRole',
      'findMinistries',
      'updateMinistries',
    ]);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
    ]);
    adminUserService.findAll.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 403 })),
      of(pageResponse()),
    );
    authSessionService.getUsername.and.returnValue('34999999999');

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'admin/usuarios', component: AdminUserManagementComponent }]),
        { provide: AdminUserService, useValue: adminUserService },
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    harness = await RouterTestingHarness.create('/admin/usuarios');

    expect(textContent()).toContain('Você não possui permissão para gerenciar usuários.');

    clickButton('Tentar novamente');

    expect(adminUserService.findAll).toHaveBeenCalledTimes(2);
    expect(textContent()).toContain('Maria Silva');
  });

  it('should apply filters explicitly with the official ministry value and return to the first page', async () => {
    await setup();

    setInputValue('#user-name', '  Maria  ');
    setInputValue('#user-phone', ' 3499 ');
    setSelectValue('#user-ministry', 'MINISTER_OF_THE_WORD');
    setSelectValue('#user-role', 'ROLE_ADMIN');
    submitFilters();

    expect(adminUserService.findAll).toHaveBeenCalledWith({
      name: 'Maria',
      phoneNumber: '3499',
      ministry: 'MINISTER_OF_THE_WORD',
      role: 'ROLE_ADMIN',
      page: 0,
      size: 10,
    });
  });

  it('should clear filters and request the first page', async () => {
    await setup();

    setInputValue('#user-name', 'Maria');
    clickButton('Limpar filtros');

    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect((query('#user-name') as HTMLInputElement).value).toBe('');
  });

  it('should navigate through pages', async () => {
    await setup(
      pageResponse({
        totalElements: 22,
        totalPages: 3,
        number: 1,
        first: false,
        last: false,
      }),
    );

    clickButton('Página anterior');
    clickButton('Próxima página');

    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 2, size: 10 });
  });

  it('should render the five ministry labels in the filter and in the panel', async () => {
    await setup();

    const filterText = query('#user-ministry').textContent ?? '';

    expect(filterText).toContain('Padre');
    expect(filterText).toContain('Leitor');
    expect(filterText).toContain('Comentarista');
    expect(filterText).toContain('Ministro da Palavra');
    expect(filterText).toContain('Ministro da Eucaristia');
  });

  it('should display zero, one and multiple ministries without picking only the first one', async () => {
    await setup(
      pageResponse({
        content: [
          person({ id: 1, ministries: [] }),
          person({ id: 2, name: 'João Souza', ministries: ['PRIEST'] }),
          person({
            id: 3,
            name: 'Ana Lima',
            ministries: ['READER', 'COMMENTATOR', 'MINISTER_OF_THE_WORD'],
          }),
        ],
        totalElements: 3,
      }),
    );

    const cells = queryAll('[data-label="Ministérios"]').map((cell) => cell.textContent?.trim());

    expect(cells[0]).toBe('Sem ministérios');
    expect(cells[1]).toBe('Padre');
    expect(cells[2]).toBe('Leitor, Comentarista, Ministro da Palavra');
  });

  it('should render the role change panel immediately after the selected person', async () => {
    await setup();

    clickButton('Alterar perfil');

    const selectedRow = query('.admin-users__row--selected');
    const detailsRow = query('.admin-users__details-row');

    expect(detailsRow.previousElementSibling).toBe(selectedRow);
    expect(detailsRow.textContent).toContain('Maria Silva');
    expect(detailsRow.textContent).toContain('Ministérios');
    expect(detailsRow.textContent).toContain('Perfil de acesso atual');
  });

  it('should expose the selected row state through aria attributes for the role panel', async () => {
    await setup();

    const button = buttonByLabel('Alterar perfil');

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(button.getAttribute('aria-controls')).toBe('role-change-panel-1');

    clickButton('Alterar perfil');

    const panel = query('#role-change-panel-1');

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(panel.getAttribute('aria-labelledby')).toBe('role-change-title-1');
    expect(query('#role-change-title-1').textContent).toContain(
      'Alterar perfil de acesso de Maria Silva',
    );
  });

  it('should focus the first available role option when the panel opens', async () => {
    await setup();

    clickButton('Alterar perfil');
    await waitForTimers();

    expect(document.activeElement).toBe(query('input[value="ROLE_ADMIN"]'));
  });

  it('should keep only one panel open and discard temporary role when another person is selected', async () => {
    await setup(
      pageResponse({
        content: [
          person(),
          person({
            id: 2,
            name: 'João Souza',
            phoneNumber: '34888888888',
            ministries: ['PRIEST'],
            roles: ['ROLE_OPERATOR'],
          }),
        ],
        totalElements: 2,
      }),
    );

    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Alterar perfil', 1);

    expect(queryAll('.admin-users__details-row').length).toBe(1);
    expect(query('.admin-users__details-row').textContent).toContain('João Souza');
    expect(query('.admin-users__details-row').textContent).toContain('Padre');
    expect(confirmButton().disabled).toBeTrue();
  });

  it('should cancel role changes without saving', async () => {
    await setup();

    clickButton('Alterar perfil');
    clickButton('Cancelar');

    expect(adminUserService.updateRole).not.toHaveBeenCalled();
    expect(textContent()).not.toContain('Alterar perfil de acesso');
  });

  it('should return focus to the original button when cancelling the role panel', async () => {
    await setup();
    const button = buttonByLabel('Alterar perfil');

    clickButton('Alterar perfil');
    await waitForTimers();
    clickButton('Cancelar');
    await waitForTimers();

    expect(document.activeElement).toBe(button);
  });

  it('should disable confirmation when no role is selected or the selected role is already the only role', async () => {
    await setup();

    clickButton('Alterar perfil');

    expect(confirmButton().disabled).toBeTrue();

    selectRole('ROLE_ADMIN');

    expect(confirmButton().disabled).toBeTrue();
  });

  it('should update a role, prevent duplicate submissions and reload the current page', async () => {
    await setup();
    const updateRoleResponse = new Subject<PersonAdmin>();
    adminUserService.updateRole.and.returnValue(updateRoleResponse.asObservable());

    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Salvar perfil');
    clickButton('Salvar perfil');

    expect(adminUserService.updateRole).toHaveBeenCalledOnceWith(1, 'ROLE_OPERATOR');

    updateRoleResponse.next(person({ roles: ['ROLE_OPERATOR'] }));
    updateRoleResponse.complete();
    harness.detectChanges();

    expect(textContent()).toContain('Perfil atualizado com sucesso.');
    expect(adminUserService.findAll).toHaveBeenCalledTimes(2);
  });

  it('should preserve filters and current page after a successful role update', async () => {
    await setup();
    adminUserService.findAll.and.returnValues(
      of(pageResponse({ totalElements: 21, totalPages: 3, first: true, last: false })),
      of(
        pageResponse({
          totalElements: 21,
          totalPages: 3,
          number: 1,
          first: false,
          last: false,
        }),
      ),
      of(
        pageResponse({
          content: [person({ roles: ['ROLE_OPERATOR'] })],
          totalElements: 11,
          totalPages: 2,
          number: 1,
          first: false,
          last: true,
        }),
      ),
    );
    adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_OPERATOR'] })));

    setInputValue('#user-name', ' Maria ');
    setSelectValue('#user-role', 'ROLE_ADMIN');
    submitFilters();
    clickButton('Próxima página');
    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Salvar perfil');

    expect(adminUserService.findAll).toHaveBeenCalledWith(
      jasmine.objectContaining({
        name: 'Maria',
        role: 'ROLE_ADMIN',
        page: 1,
        size: 10,
      }),
    );
    expect(textContent()).toContain('11 resultado(s) encontrado(s).');
  });

  it('should load the previous page when the current page becomes empty after a role update', async () => {
    await setup(pageResponse({ number: 1, totalPages: 2, totalElements: 11, first: false }));
    adminUserService.findAll.and.returnValues(
      of(
        pageResponse({
          content: [],
          number: 1,
          totalPages: 1,
          totalElements: 1,
          first: false,
          empty: true,
        }),
      ),
      of(pageResponse({ content: [person({ id: 3, name: 'Ana Lima' })] })),
    );
    adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_OPERATOR'] })));

    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Salvar perfil');

    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 1, size: 10 });
    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(textContent()).toContain('Ana Lima');
  });

  it('should keep the panel and item after a 404 role update error', async () => {
    await setup();
    adminUserService.updateRole.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Salvar perfil');

    expect(textContent()).toContain('A pessoa selecionada não foi encontrada.');
    expect(textContent()).toContain('Alterar perfil de acesso de Maria Silva');
    expect(textContent()).toContain('Maria Silva');
    expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
  });

  it('should show friendly 409 messages and preserve the item after errors', async () => {
    await setup();
    adminUserService.updateRole.and.returnValues(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { message: 'Voce nao pode remover o seu proprio perfil administrativo.' },
          }),
      ),
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              message: 'O ultimo administrador do sistema nao pode ter seu perfil alterado.',
            },
          }),
      ),
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Outra regra' } })),
    );

    clickButton('Alterar perfil');
    selectRole('ROLE_OPERATOR');
    clickButton('Salvar perfil');

    expect(textContent()).toContain('Você não pode remover o seu próprio perfil administrativo.');
    expect(textContent()).toContain('Maria Silva');

    component.selectRole('ROLE_OPERATOR');
    component.confirmRoleChange();
    harness.detectChanges();

    expect(textContent()).toContain(
      'Não é possível remover o perfil do último administrador do sistema.',
    );

    component.selectRole('ROLE_OPERATOR');
    component.confirmRoleChange();
    harness.detectChanges();

    expect(textContent()).toContain(
      'Não foi possível alterar o perfil devido a uma regra administrativa.',
    );
  });

  it('should visually block self-demotion for the authenticated administrator', async () => {
    await setup(pageResponse(), '34999999999');

    clickButton('Alterar perfil');

    const operatorInput = query('input[value="ROLE_OPERATOR"]') as HTMLInputElement;

    expect(operatorInput.disabled).toBeTrue();
    expect(textContent()).toContain('O próprio perfil administrativo não pode ser removido.');
  });

  it('should not expose sensitive fields', async () => {
    await setup();

    const text = textContent();

    expect(text).not.toContain('password');
    expect(text).not.toContain('birthdayDate');
    expect(text).not.toContain('access_token');
    expect(text).not.toContain('"roles"');
  });

  it('should open the ministries panel, request the GET and load the official set', async () => {
    await setup(pageResponse({ content: [person({ ministries: ['READER', 'COMMENTATOR'] })] }));
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['PRIEST'] })));

    clickButton('Gerenciar ministérios');

    expect(adminUserService.findMinistries).toHaveBeenCalledOnceWith(1);
    expect(ministryCheckbox(1, 'PRIEST').checked).toBeTrue();
    expect(ministryCheckbox(1, 'READER').checked).toBeFalse();
    expect(ministryCheckbox(1, 'COMMENTATOR').checked).toBeFalse();
  });

  it('should render the five ministry checkboxes in the panel', async () => {
    await setup();

    clickButton('Gerenciar ministérios');

    expect(queryAll('#ministries-panel-1 input[type="checkbox"]').length).toBe(5);
  });

  it('should save the full set of selected ministries', async () => {
    await setup(pageResponse({ content: [person({ ministries: ['READER'] })] }));
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
    const updateResponse = new Subject<PersonMinistriesResponse>();
    adminUserService.updateMinistries.and.returnValue(updateResponse.asObservable());

    clickButton('Gerenciar ministérios');
    toggleMinistryCheckbox(1, 'COMMENTATOR');
    toggleMinistryCheckbox(1, 'PRIEST');
    clickButton('Salvar ministérios');
    clickButton('Salvar ministérios');

    expect(adminUserService.updateMinistries).toHaveBeenCalledOnceWith(1, [
      'READER',
      'COMMENTATOR',
      'PRIEST',
    ]);

    updateResponse.next(ministriesResponse({ ministries: ['READER', 'COMMENTATOR', 'PRIEST'] }));
    updateResponse.complete();
    harness.detectChanges();

    expect(textContent()).toContain('Ministérios atualizados com sucesso.');
    expect(adminUserService.findAll).toHaveBeenCalledTimes(2);
  });

  it('should save an empty ministries set', async () => {
    await setup(pageResponse({ content: [person({ ministries: ['READER'] })] }));
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
    adminUserService.updateMinistries.and.returnValue(of(ministriesResponse({ ministries: [] })));

    clickButton('Gerenciar ministérios');
    toggleMinistryCheckbox(1, 'READER');
    clickButton('Salvar ministérios');

    expect(adminUserService.updateMinistries).toHaveBeenCalledOnceWith(1, []);
  });

  it('should never send duplicate ministries even if the backend returns duplicates', async () => {
    await setup(pageResponse({ content: [person({ ministries: ['READER'] })] }));
    adminUserService.findMinistries.and.returnValue(
      of(ministriesResponse({ ministries: ['READER', 'READER', 'COMMENTATOR'] })),
    );
    adminUserService.updateMinistries.and.returnValue(
      of(ministriesResponse({ ministries: ['READER', 'COMMENTATOR'] })),
    );

    clickButton('Gerenciar ministérios');
    clickButton('Salvar ministérios');

    expect(adminUserService.updateMinistries).toHaveBeenCalledOnceWith(1, ['READER', 'COMMENTATOR']);
  });

  it('should cancel ministries editing without sending a PUT', async () => {
    await setup();

    clickButton('Gerenciar ministérios');
    clickButton('Cancelar');

    expect(adminUserService.updateMinistries).not.toHaveBeenCalled();
    expect(textContent()).not.toContain('Gerenciar ministérios de Maria Silva');
  });

  it('should return focus to the ministries button on cancel', async () => {
    await setup();
    const button = buttonByLabel('Gerenciar ministérios');

    clickButton('Gerenciar ministérios');
    await waitForTimers();
    clickButton('Cancelar');
    await waitForTimers();

    expect(document.activeElement).toBe(button);
  });

  it('should keep only one inline panel open when switching between role and ministries editors', async () => {
    await setup();

    clickButton('Alterar perfil');
    expect(query('#role-change-panel-1')).toBeTruthy();

    clickButton('Gerenciar ministérios');
    expect(queryAll('.admin-users__details-row').length).toBe(1);
    expect(textContent()).toContain('Gerenciar ministérios de Maria Silva');
    expect(textContent()).not.toContain('Alterar perfil de acesso de Maria Silva');

    clickButton('Alterar perfil');
    expect(queryAll('.admin-users__details-row').length).toBe(1);
    expect(textContent()).toContain('Alterar perfil de acesso de Maria Silva');
    expect(textContent()).not.toContain('Gerenciar ministérios de Maria Silva');
  });

  it('should discard a pending ministries request when switching to another person quickly', async () => {
    await setup(
      pageResponse({
        content: [person({ id: 1, name: 'Maria Silva' }), person({ id: 2, name: 'João Souza' })],
        totalElements: 2,
      }),
    );

    const firstRequest = new Subject<PersonMinistriesResponse>();
    const secondRequest = new Subject<PersonMinistriesResponse>();
    adminUserService.findMinistries.and.returnValues(
      firstRequest.asObservable(),
      secondRequest.asObservable(),
    );

    clickButton('Gerenciar ministérios', 0);
    clickButton('Gerenciar ministérios', 1);

    expect(textContent()).toContain('Carregando ministérios...');

    firstRequest.next(ministriesResponse({ id: 1, ministries: ['PRIEST'] }));
    firstRequest.complete();
    harness.detectChanges();

    expect(textContent()).toContain('Gerenciar ministérios de João Souza');
    expect(textContent()).toContain('Carregando ministérios...');

    secondRequest.next(ministriesResponse({ id: 2, ministries: ['READER'] }));
    secondRequest.complete();
    harness.detectChanges();

    expect(ministryCheckbox(2, 'READER').checked).toBeTrue();
    expect(adminUserService.findMinistries).toHaveBeenCalledTimes(2);
  });

  it('should preserve filters and current page after a successful ministries update', async () => {
    await setup();
    adminUserService.findAll.and.returnValues(
      of(pageResponse({ totalElements: 21, totalPages: 3, first: true, last: false })),
      of(
        pageResponse({
          totalElements: 21,
          totalPages: 3,
          number: 1,
          first: false,
          last: false,
        }),
      ),
      of(
        pageResponse({
          content: [person({ ministries: ['COMMENTATOR'] })],
          totalElements: 21,
          totalPages: 3,
          number: 1,
          first: false,
          last: false,
        }),
      ),
    );
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
    adminUserService.updateMinistries.and.returnValue(of(ministriesResponse({ ministries: ['COMMENTATOR'] })));

    setInputValue('#user-name', ' Maria ');
    setSelectValue('#user-role', 'ROLE_ADMIN');
    submitFilters();
    clickButton('Próxima página');
    clickButton('Gerenciar ministérios');
    toggleMinistryCheckbox(1, 'COMMENTATOR');
    toggleMinistryCheckbox(1, 'READER');
    clickButton('Salvar ministérios');

    expect(adminUserService.findAll).toHaveBeenCalledWith(
      jasmine.objectContaining({
        name: 'Maria',
        role: 'ROLE_ADMIN',
        page: 1,
        size: 10,
      }),
    );
  });

  it('should load the previous page when the current page becomes empty after a ministries update', async () => {
    await setup(pageResponse({ number: 1, totalPages: 2, totalElements: 11, first: false }));
    adminUserService.findAll.and.returnValues(
      of(
        pageResponse({
          content: [],
          number: 1,
          totalPages: 1,
          totalElements: 1,
          first: false,
          empty: true,
        }),
      ),
      of(pageResponse({ content: [person({ id: 3, name: 'Ana Lima' })] })),
    );
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
    adminUserService.updateMinistries.and.returnValue(of(ministriesResponse({ ministries: [] })));

    clickButton('Gerenciar ministérios');
    toggleMinistryCheckbox(1, 'READER');
    clickButton('Salvar ministérios');

    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 1, size: 10 });
    expect(adminUserService.findAll).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(textContent()).toContain('Ana Lima');
  });

  it('should keep the ministries panel open and preserve selections on a 409 conflict', async () => {
    await setup(pageResponse({ content: [person({ ministries: ['READER'] })] }));
    adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
    adminUserService.updateMinistries.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );

    clickButton('Gerenciar ministérios');
    toggleMinistryCheckbox(1, 'COMMENTATOR');
    clickButton('Salvar ministérios');

    expect(textContent()).toContain('Não é possível remover um ministério vinculado a uma escala.');
    expect(textContent()).toContain('Gerenciar ministérios de Maria Silva');
    expect(ministryCheckbox(1, 'READER').checked).toBeTrue();
    expect(ministryCheckbox(1, 'COMMENTATOR').checked).toBeTrue();
    expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
  });

  it('should show an error inside the panel when loading ministries fails', async () => {
    await setup();
    adminUserService.findMinistries.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    clickButton('Gerenciar ministérios');

    expect(textContent()).toContain('A pessoa selecionada não foi encontrada.');
    expect(queryAll('#ministries-panel-1 input[type="checkbox"]').length).toBe(0);
  });

  it('should expose aria attributes and a unique id for the ministries panel', async () => {
    await setup();
    const button = buttonByLabel('Gerenciar ministérios');

    expect(button.getAttribute('aria-expanded')).toBe('false');
    expect(button.getAttribute('aria-controls')).toBe('ministries-panel-1');

    clickButton('Gerenciar ministérios');

    const panel = query('#ministries-panel-1');

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(panel.getAttribute('aria-labelledby')).toBe('ministries-title-1');
    expect(query('#ministries-title-1').textContent).toContain(
      'Gerenciar ministérios de Maria Silva',
    );
  });

  it('should focus the first checkbox once ministries load', async () => {
    await setup();

    clickButton('Gerenciar ministérios');
    await waitForTimers();

    expect(document.activeElement).toBe(ministryCheckbox(1, 'PRIEST'));
  });

  describe('query parameter restoration', () => {
    it('should use defaults when no query parameters are present', async () => {
      await setup();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
    });

    it('should keep the URL clean when no query parameters are present', async () => {
      await setup();
      await harness.fixture.whenStable();

      expect(navigateSpy).not.toHaveBeenCalled();
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should restore name from the URL', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?name=Maria');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ name: 'Maria' }),
      );
      expect((query('#user-name') as HTMLInputElement).value).toBe('Maria');
    });

    it('should restore phoneNumber from the URL', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?phoneNumber=34999999999');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ phoneNumber: '34999999999' }),
      );
      expect((query('#user-phone') as HTMLInputElement).value).toBe('34999999999');
    });

    it('should restore a valid ministry from the URL', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?ministry=READER');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ ministry: 'READER' }),
      );
      expect((query('#user-ministry') as HTMLSelectElement).value).toBe('READER');
    });

    it('should restore a valid role from the URL', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?role=ROLE_ADMIN');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ role: 'ROLE_ADMIN' }),
      );
      expect((query('#user-role') as HTMLSelectElement).value).toBe('ROLE_ADMIN');
    });

    it('should restore a valid page from the URL', async () => {
      await setup(
        pageResponse({ number: 2, totalPages: 3, totalElements: 25, first: false, last: false }),
        '34000000000',
        '/admin/usuarios?page=2',
      );

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ page: 2 }),
      );
    });

    it('should restore the complete combination of filters from the URL', async () => {
      await setup(
        pageResponse(),
        '34000000000',
        '/admin/usuarios?name=Joao&phoneNumber=34999999999&ministry=PRIEST&role=ROLE_OPERATOR&page=1',
      );

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({
        name: 'Joao',
        phoneNumber: '34999999999',
        ministry: 'PRIEST',
        role: 'ROLE_OPERATOR',
        page: 1,
        size: 10,
      });
      expect((query('#user-name') as HTMLInputElement).value).toBe('Joao');
      expect((query('#user-phone') as HTMLInputElement).value).toBe('34999999999');
      expect((query('#user-ministry') as HTMLSelectElement).value).toBe('PRIEST');
      expect((query('#user-role') as HTMLSelectElement).value).toBe('ROLE_OPERATOR');
    });

    it('should execute only one request on initialization regardless of query params', async () => {
      await setup(
        pageResponse(),
        '34000000000',
        '/admin/usuarios?name=Joao&phoneNumber=34999999999&ministry=PRIEST&role=ROLE_OPERATOR&page=0',
      );

      expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should trim a restored name', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?name=%20%20Maria%20%20');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ name: 'Maria' }),
      );
      expect((query('#user-name') as HTMLInputElement).value).toBe('Maria');
    });

    it('should trim a restored phoneNumber', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?phoneNumber=%20%2034999999999%20');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ phoneNumber: '34999999999' }),
      );
    });
  });

  describe('applyFilters URL synchronization', () => {
    it('should update the URL when applying filters', async () => {
      await setup();
      navigateSpy.calls.reset();

      setInputValue('#user-name', 'Maria');
      submitFilters();
      await harness.fixture.whenStable();

      expect(location.path()).toBe('/admin/usuarios?name=Maria');
    });

    it('should use replaceUrl when applying filters', async () => {
      await setup();
      navigateSpy.calls.reset();

      setInputValue('#user-name', 'Maria');
      submitFilters();

      expect(navigateSpy).toHaveBeenCalledTimes(1);
      expect(navigateSpy.calls.mostRecent().args[1].replaceUrl).toBeTrue();
      expect(navigateSpy.calls.mostRecent().args[1].queryParamsHandling).toBe('merge');
    });

    it('should reset page to zero in the URL when applying filters from a non-zero page', async () => {
      await setup(
        pageResponse({ number: 3, totalPages: 5, totalElements: 50, first: false, last: false }),
        '34000000000',
        '/admin/usuarios?page=3',
      );
      await harness.fixture.whenStable();
      expect(location.path()).toContain('page=3');

      adminUserService.findAll.and.returnValue(of(pageResponse({ number: 0 })));
      setInputValue('#user-name', 'Maria');
      submitFilters();
      await harness.fixture.whenStable();

      expect(location.path()).not.toContain('page=');
      expect(location.path()).toContain('name=Maria');
    });

    it('should execute only one request when applying filters', async () => {
      await setup();
      adminUserService.findAll.calls.reset();

      setInputValue('#user-name', 'Maria');
      submitFilters();

      expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should show the trimmed value in the form after applying filters with surrounding spaces', async () => {
      await setup();

      setInputValue('#user-name', '  Maria Silva  ');
      setInputValue('#user-phone', '  34999999999  ');
      submitFilters();
      await harness.fixture.whenStable();

      expect((query('#user-name') as HTMLInputElement).value).toBe('Maria Silva');
      expect((query('#user-phone') as HTMLInputElement).value).toBe('34999999999');
      expect(location.path()).toContain('name=Maria%20Silva');
      expect(adminUserService.findAll).toHaveBeenCalledWith(
        jasmine.objectContaining({ name: 'Maria Silva', phoneNumber: '34999999999' }),
      );
    });
  });

  describe('clearFilters URL synchronization', () => {
    it('should remove the known parameters from the URL when clearing filters', async () => {
      await setup(
        pageResponse(),
        '34000000000',
        '/admin/usuarios?name=Maria&phoneNumber=34999999999&ministry=READER&role=ROLE_ADMIN&page=1',
      );
      await harness.fixture.whenStable();

      clickButton('Limpar filtros');
      await harness.fixture.whenStable();

      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should preserve an unknown query parameter when clearing filters', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?name=Maria&foo=bar');
      await harness.fixture.whenStable();

      clickButton('Limpar filtros');
      await harness.fixture.whenStable();

      expect(location.path()).toBe('/admin/usuarios?foo=bar');
    });

    it('should execute only one request when clearing filters', async () => {
      await setup();
      adminUserService.findAll.calls.reset();

      clickButton('Limpar filtros');

      expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
    });
  });

  describe('pagination URL synchronization', () => {
    it('should only change page in the URL when going to the previous page', async () => {
      await setup(
        pageResponse({
          number: 1,
          totalPages: 3,
          totalElements: 25,
          first: false,
          last: false,
        }),
        '34000000000',
        '/admin/usuarios?name=Maria&role=ROLE_ADMIN&page=1',
      );
      await harness.fixture.whenStable();
      adminUserService.findAll.and.returnValue(
        of(pageResponse({ number: 0, totalPages: 3, totalElements: 25, first: true, last: false })),
      );

      clickButton('Página anterior');
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('name=Maria');
      expect(path).toContain('role=ROLE_ADMIN');
      expect(path).not.toContain('page=');
    });

    it('should only change page in the URL when going to the next page', async () => {
      await setup(
        pageResponse({
          number: 0,
          totalPages: 3,
          totalElements: 25,
          first: true,
          last: false,
        }),
        '34000000000',
        '/admin/usuarios?name=Maria&role=ROLE_ADMIN',
      );
      await harness.fixture.whenStable();
      adminUserService.findAll.and.returnValue(
        of(
          pageResponse({
            number: 1,
            totalPages: 3,
            totalElements: 25,
            first: false,
            last: false,
          }),
        ),
      );

      clickButton('Próxima página');
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('name=Maria');
      expect(path).toContain('role=ROLE_ADMIN');
      expect(path).toContain('page=1');
    });

    it('should send the active filters, not the form values, while paginating', async () => {
      await setup(
        pageResponse({
          number: 0,
          totalPages: 3,
          totalElements: 25,
          first: true,
          last: false,
        }),
      );

      setInputValue('#user-name', 'Maria');
      submitFilters();

      setInputValue('#user-name', 'Changed but not submitted');

      adminUserService.findAll.calls.reset();
      clickButton('Próxima página');

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ name: 'Maria', page: 1 }),
      );
    });
  });

  describe('invalid query parameters', () => {
    it('should remove an invalid ministry from the URL and use no filter', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?ministry=INVALID');
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should remove an invalid role from the URL and use no filter', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?role=ADMIN');
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should remove a negative page from the URL and use zero', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?page=-1');
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should remove a decimal page from the URL and use zero', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?page=1.5');
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should remove a textual page from the URL and use zero', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?page=abc');
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith({ page: 0, size: 10 });
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should not duplicate the request when correcting invalid query params', async () => {
      await setup(
        pageResponse(),
        '34000000000',
        '/admin/usuarios?ministry=INVALID&role=ADMIN&page=-1',
      );
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
    });

    it('should preserve unknown query parameters while correcting invalid ones', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?ministry=INVALID&foo=bar');
      await harness.fixture.whenStable();

      expect(location.path()).toContain('foo=bar');
      expect(location.path()).not.toContain('ministry=');
    });
  });

  describe('page.number normalization', () => {
    it('should correct activeFilters to reflect the effective page returned by the backend', async () => {
      await setup(
        pageResponse({ number: 0, totalPages: 1, totalElements: 1 }),
        '34000000000',
        '/admin/usuarios?page=5',
      );
      await harness.fixture.whenStable();

      expect(component.activeFilters().page).toBe(0);
    });

    it('should correct the URL page when the backend returns a different page number', async () => {
      await setup(
        pageResponse({ number: 0, totalPages: 1, totalElements: 1 }),
        '34000000000',
        '/admin/usuarios?page=5',
      );
      await harness.fixture.whenStable();

      expect(location.path()).not.toContain('page=5');
      expect(location.path()).toBe('/admin/usuarios');
    });

    it('should not call the backend again when normalizing page.number', async () => {
      await setup(
        pageResponse({ number: 0, totalPages: 1, totalElements: 1 }),
        '34000000000',
        '/admin/usuarios?page=5',
      );
      await harness.fixture.whenStable();

      expect(adminUserService.findAll).toHaveBeenCalledTimes(1);
    });
  });

  describe('empty page after change', () => {
    it('should load the previous page when it becomes empty', async () => {
      await setup(pageResponse({ number: 1, totalPages: 2, totalElements: 11, first: false }));
      adminUserService.findAll.and.returnValues(
        of(
          pageResponse({
            content: [],
            number: 1,
            totalPages: 1,
            totalElements: 1,
            first: false,
            empty: true,
          }),
        ),
        of(pageResponse({ content: [person({ id: 3, name: 'Ana Lima' })] })),
      );
      adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_OPERATOR'] })));

      clickButton('Alterar perfil');
      selectRole('ROLE_OPERATOR');
      clickButton('Salvar perfil');

      expect(textContent()).toContain('Ana Lima');
    });

    it('should correct the URL page before requesting the previous page', async () => {
      await setup(
        pageResponse({ number: 1, totalPages: 2, totalElements: 11, first: false }),
        '34000000000',
        '/admin/usuarios?page=1',
      );
      await harness.fixture.whenStable();
      adminUserService.findAll.and.returnValues(
        of(
          pageResponse({
            content: [],
            number: 1,
            totalPages: 1,
            totalElements: 1,
            first: false,
            empty: true,
          }),
        ),
        of(pageResponse({ content: [person({ id: 3, name: 'Ana Lima' })] })),
      );
      adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_OPERATOR'] })));

      clickButton('Alterar perfil');
      selectRole('ROLE_OPERATOR');
      clickButton('Salvar perfil');
      await harness.fixture.whenStable();

      expect(location.path()).not.toContain('page=1');
    });
  });

  describe('role and ministries updates preserving context', () => {
    it('should preserve filters and page in the URL after a role update', async () => {
      await setup(
        pageResponse({ number: 0, totalPages: 1 }),
        '34000000000',
        '/admin/usuarios?name=Maria&role=ROLE_ADMIN',
      );
      await harness.fixture.whenStable();
      navigateSpy.calls.reset();
      adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_ADMIN'] })));

      clickButton('Alterar perfil');
      selectRole('ROLE_OPERATOR');
      clickButton('Salvar perfil');
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('name=Maria');
      expect(path).toContain('role=ROLE_ADMIN');
    });

    it('should preserve filters and page in the URL after a ministries update', async () => {
      await setup(
        pageResponse({ number: 0, totalPages: 1 }),
        '34000000000',
        '/admin/usuarios?name=Maria&ministry=READER',
      );
      await harness.fixture.whenStable();
      adminUserService.updateMinistries.and.returnValue(of(ministriesResponse({ ministries: [] })));

      clickButton('Gerenciar ministérios');
      toggleMinistryCheckbox(1, 'READER');
      clickButton('Salvar ministérios');
      await harness.fixture.whenStable();

      const path = location.path();

      expect(path).toContain('name=Maria');
      expect(path).toContain('ministry=READER');
    });

    it('should let a person disappear from the page after a role update that no longer matches the active filter', async () => {
      await setup(
        pageResponse({ content: [person({ roles: ['ROLE_ADMIN'] })], number: 0, totalPages: 1 }),
        '34000000000',
        '/admin/usuarios?role=ROLE_ADMIN',
      );
      adminUserService.findAll.and.returnValue(
        of(pageResponse({ content: [], number: 0, totalPages: 0, totalElements: 0, empty: true })),
      );
      adminUserService.updateRole.and.returnValue(of(person({ roles: ['ROLE_OPERATOR'] })));

      clickButton('Alterar perfil');
      selectRole('ROLE_OPERATOR');
      clickButton('Salvar perfil');

      expect(textContent()).toContain('Nenhuma pessoa foi encontrada com os filtros informados.');
    });

    it('should let a person disappear from the page after a ministries update that no longer matches the active filter', async () => {
      await setup(
        pageResponse({ content: [person({ ministries: ['READER'] })], number: 0, totalPages: 1 }),
        '34000000000',
        '/admin/usuarios?ministry=READER',
      );
      adminUserService.findMinistries.and.returnValue(of(ministriesResponse({ ministries: ['READER'] })));
      adminUserService.findAll.and.returnValue(
        of(pageResponse({ content: [], number: 0, totalPages: 0, totalElements: 0, empty: true })),
      );
      adminUserService.updateMinistries.and.returnValue(of(ministriesResponse({ ministries: ['PRIEST'] })));

      clickButton('Gerenciar ministérios');
      toggleMinistryCheckbox(1, 'READER');
      toggleMinistryCheckbox(1, 'PRIEST');
      clickButton('Salvar ministérios');

      expect(textContent()).toContain('Nenhuma pessoa foi encontrada com os filtros informados.');
    });
  });

  describe('retry', () => {
    it('should reuse activeFilters on retry', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?name=Maria&role=ROLE_ADMIN');
      adminUserService.findAll.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      setInputValue('#user-name', 'Changed but not submitted');

      adminUserService.findAll.calls.reset();
      adminUserService.findAll.and.returnValue(of(pageResponse()));
      component.retry();

      expect(adminUserService.findAll).toHaveBeenCalledOnceWith(
        jasmine.objectContaining({ name: 'Maria', role: 'ROLE_ADMIN' }),
      );
    });

    it('should not change the URL on retry when it already represents activeFilters', async () => {
      await setup(pageResponse(), '34000000000', '/admin/usuarios?name=Maria');
      await harness.fixture.whenStable();
      navigateSpy.calls.reset();

      component.retry();

      expect(navigateSpy).not.toHaveBeenCalled();
    });
  });

  function query(selector: string): Element {
    const element = harness.routeNativeElement?.querySelector(selector) ?? null;

    expect(element).not.toBeNull();

    return element as Element;
  }

  function queryAll(selector: string): Element[] {
    return Array.from(harness.routeNativeElement?.querySelectorAll(selector) ?? []);
  }

  function setInputValue(selector: string, value: string): void {
    const input = query(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function setSelectValue(selector: string, value: string): void {
    const select = query(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  function submitFilters(): void {
    const form = query('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    harness.detectChanges();
  }

  function buttonByLabel(label: string, index = 0): HTMLButtonElement {
    const button = Array.from(harness.routeNativeElement?.querySelectorAll('button') ?? [])
      .filter((currentButton) => currentButton.textContent?.trim() === label)[index] as
      | HTMLButtonElement
      | undefined;

    expect(button).toBeDefined();
    return button as HTMLButtonElement;
  }

  function clickButton(label: string, index = 0): void {
    const button = buttonByLabel(label, index);
    button.click();
    harness.detectChanges();
  }

  function selectRole(role: string): void {
    const input = query(`input[value="${role}"]`) as HTMLInputElement;
    input.checked = true;
    input.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  function confirmButton(): HTMLButtonElement {
    return Array.from(harness.routeNativeElement?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.trim() === 'Salvar perfil',
    ) as HTMLButtonElement;
  }

  function ministryCheckbox(personId: number, ministry: string): HTMLInputElement {
    return query(`#ministry-checkbox-${personId}-${ministry}`) as HTMLInputElement;
  }

  function toggleMinistryCheckbox(personId: number, ministry: string): void {
    const checkbox = ministryCheckbox(personId, ministry);
    checkbox.checked = !checkbox.checked;
    checkbox.dispatchEvent(new Event('change'));
    harness.detectChanges();
  }

  function textContent(): string {
    return harness.routeNativeElement?.textContent ?? '';
  }

  function waitForTimers(): Promise<void> {
    return new Promise((resolve) => {
      window.setTimeout(resolve);
    });
  }

  function pageResponse(overrides: Partial<PersonAdminPage> = {}): PersonAdminPage {
    return {
      content: [person()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
      first: true,
      last: true,
      empty: false,
      ...overrides,
    };
  }

  function person(overrides: Partial<PersonAdmin> = {}): PersonAdmin {
    return {
      id: 1,
      name: 'Maria Silva',
      phoneNumber: '34999999999',
      ministries: ['READER'],
      roles: ['ROLE_ADMIN'],
      ...overrides,
    };
  }

  function ministriesResponse(
    overrides: Partial<PersonMinistriesResponse> = {},
  ): PersonMinistriesResponse {
    return {
      id: 1,
      ministries: ['READER'],
      ...overrides,
    };
  }
});
