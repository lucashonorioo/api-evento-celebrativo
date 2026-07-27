import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { PersonAdmin, PersonAdminPage, PersonMinistriesResponse } from '../admin-user.models';
import { AdminUserService } from '../admin-user.service';
import { AdminUserManagementComponent } from './admin-user-management.component';

describe('AdminUserManagementComponent', () => {
  let fixture: ComponentFixture<AdminUserManagementComponent>;
  let adminUserService: jasmine.SpyObj<AdminUserService>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;

  async function setup(
    page: PersonAdminPage = pageResponse(),
    username: string | null = '34000000000',
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
      imports: [AdminUserManagementComponent],
      providers: [
        provideRouter([]),
        { provide: AdminUserService, useValue: adminUserService },
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUserManagementComponent);
    fixture.detectChanges();
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

    const action = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('a'),
    ).find((link) => link.textContent?.trim() === 'Consultar categorias ministeriais') as
      | HTMLAnchorElement
      | undefined;

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
      imports: [AdminUserManagementComponent],
      providers: [
        provideRouter([]),
        { provide: AdminUserService, useValue: adminUserService },
        { provide: AuthSessionService, useValue: authSessionService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUserManagementComponent);
    fixture.detectChanges();

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
    fixture.detectChanges();

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

    fixture.componentInstance.selectRole('ROLE_OPERATOR');
    fixture.componentInstance.confirmRoleChange();
    fixture.detectChanges();

    expect(textContent()).toContain(
      'Não é possível remover o perfil do último administrador do sistema.',
    );

    fixture.componentInstance.selectRole('ROLE_OPERATOR');
    fixture.componentInstance.confirmRoleChange();
    fixture.detectChanges();

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
    fixture.detectChanges();

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
    fixture.detectChanges();

    expect(textContent()).toContain('Gerenciar ministérios de João Souza');
    expect(textContent()).toContain('Carregando ministérios...');

    secondRequest.next(ministriesResponse({ id: 2, ministries: ['READER'] }));
    secondRequest.complete();
    fixture.detectChanges();

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

  function query(selector: string): Element {
    const element = (fixture.nativeElement as HTMLElement).querySelector(selector);

    expect(element).not.toBeNull();

    return element as Element;
  }

  function queryAll(selector: string): Element[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector));
  }

  function setInputValue(selector: string, value: string): void {
    const input = query(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  function setSelectValue(selector: string, value: string): void {
    const select = query(selector) as HTMLSelectElement;
    select.value = value;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function submitFilters(): void {
    const form = query('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  }

  function buttonByLabel(label: string, index = 0): HTMLButtonElement {
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).filter((currentButton) => currentButton.textContent?.trim() === label)[index] as
      | HTMLButtonElement
      | undefined;

    expect(button).toBeDefined();
    return button as HTMLButtonElement;
  }

  function clickButton(label: string, index = 0): void {
    const button = buttonByLabel(label, index);
    button.click();
    fixture.detectChanges();
  }

  function selectRole(role: string): void {
    const input = query(`input[value="${role}"]`) as HTMLInputElement;
    input.checked = true;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function confirmButton(): HTMLButtonElement {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find(
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
    fixture.detectChanges();
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
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
