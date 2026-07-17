import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import { PersonAdmin, PersonAdminPage } from '../admin-user.models';
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
    ]);
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'getUsername',
    ]);
    adminUserService.findAll.and.returnValue(of(page));
    authSessionService.getUsername.and.returnValue(username);

    await TestBed.configureTestingModule({
      imports: [AdminUserManagementComponent],
      providers: [
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
    expect(textContent()).toContain('Usuários do sistema');
    expect(textContent()).toContain('Maria Silva');
    expect(textContent()).toContain('(34) 99999-9999');
    expect(textContent()).toContain('Leitor');
    expect(textContent()).toContain('Administrador');
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

  it('should apply filters explicitly and return to the first page', async () => {
    await setup();

    setInputValue('#user-name', '  Maria  ');
    setInputValue('#user-phone', ' 3499 ');
    setSelectValue('#user-person-type', 'minister_of_the_word');
    setSelectValue('#user-role', 'ROLE_ADMIN');
    submitFilters();

    expect(adminUserService.findAll).toHaveBeenCalledWith({
      name: 'Maria',
      phoneNumber: '3499',
      personType: 'minister_of_the_word',
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

  it('should render multiple roles and the replacement explanation', async () => {
    await setup(pageResponse({ content: [person({ roles: ['ROLE_ADMIN', 'ROLE_OPERATOR'] })] }));

    expect(textContent()).toContain('Administrador, Operador');

    clickButton('Alterar perfil');

    expect(textContent()).toContain('Ao salvar, todos os perfis atuais serão substituídos');
    expect(textContent()).toContain('novo perfil selecionado.');
  });

  it('should render the role change panel immediately after the selected person', async () => {
    await setup();

    clickButton('Alterar perfil');

    const selectedRow = query('.admin-users__row--selected');
    const detailsRow = query('.admin-users__details-row');

    expect(detailsRow.previousElementSibling).toBe(selectedRow);
    expect(detailsRow.textContent).toContain('Maria Silva');
    expect(detailsRow.textContent).toContain('Categoria da pessoa');
    expect(detailsRow.textContent).toContain('Perfil de acesso atual');
  });

  it('should expose the selected row state through aria attributes', async () => {
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
            personType: 'priest',
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

  it('should return focus to the original button when cancelling', async () => {
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

  it('should preserve filters and current page after a successful update', async () => {
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

  it('should load the previous page when the current page becomes empty after an update', async () => {
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
      personType: 'reader',
      roles: ['ROLE_ADMIN'],
      ...overrides,
    };
  }
});
