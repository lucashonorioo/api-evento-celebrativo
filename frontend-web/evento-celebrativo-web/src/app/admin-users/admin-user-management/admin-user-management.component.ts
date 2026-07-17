import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  ElementRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, map, of, Subject, switchMap } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import {
  PersonAdmin,
  PersonAdminFilters,
  PersonAdminPage,
  PersonType,
  UserRole,
} from '../admin-user.models';
import { AdminUserService } from '../admin-user.service';

const DEFAULT_PAGE_SIZE = 10;

interface PersonTypeOption {
  readonly value: PersonType;
  readonly label: string;
}

interface UserRoleOption {
  readonly value: UserRole;
  readonly label: string;
}

type QueryResult =
  | {
      readonly type: 'success';
      readonly query: PersonAdminFilters;
      readonly page: PersonAdminPage;
    }
  | {
      readonly type: 'error';
      readonly query: PersonAdminFilters;
      readonly error: unknown;
    };

@Component({
  selector: 'app-admin-user-management',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './admin-user-management.component.html',
  styleUrl: './admin-user-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUserManagementComponent implements OnInit {
  private readonly adminUserService = inject(AdminUserService);
  private readonly authSessionService = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly queryRequests = new Subject<PersonAdminFilters>();
  private lastRoleChangeButton: HTMLButtonElement | null = null;

  readonly filtersForm = this.formBuilder.group({
    name: [''],
    phoneNumber: [''],
    personType: ['' as PersonType | ''],
    role: ['' as UserRole | ''],
  });

  readonly personTypeOptions: readonly PersonTypeOption[] = [
    { value: 'reader', label: 'Leitor' },
    { value: 'commentator', label: 'Comentarista' },
    { value: 'minister_of_the_word', label: 'Ministro da Palavra' },
    { value: 'eucharistic_minister', label: 'Ministro da Eucaristia' },
    { value: 'priest', label: 'Padre' },
  ];
  readonly roleOptions: readonly UserRoleOption[] = [
    { value: 'ROLE_ADMIN', label: 'Administrador' },
    { value: 'ROLE_OPERATOR', label: 'Operador' },
  ];

  readonly people = signal<PersonAdmin[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly roleChangeErrorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  readonly isFirstPage = signal(true);
  readonly isLastPage = signal(true);
  readonly activeFilters = signal<PersonAdminFilters>(emptyFilters());
  readonly pendingRoleChange = signal<PersonAdmin | null>(null);
  readonly selectedRole = signal<UserRole | null>(null);

  private readonly authenticatedUsername = this.authSessionService.getUsername();

  ngOnInit(): void {
    this.queryRequests
      .pipe(
        switchMap((query) => {
          this.isLoading.set(true);

          return this.adminUserService.findAll(query).pipe(
            map((page): QueryResult => ({ type: 'success', query, page })),
            catchError((error: unknown) => of({ type: 'error', query, error } satisfies QueryResult)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        this.isLoading.set(false);

        if (result.type === 'error') {
          this.errorMessage.set(listErrorMessageFor(result.error));
          return;
        }

        if (result.page.empty && result.query.page > 0 && result.page.totalElements > 0) {
          this.loadPage(result.query.page - 1);
          return;
        }

        this.applyPage(result.page);
      });

    this.loadPage(0);
  }

  applyFilters(): void {
    this.loadPage(0, this.createFiltersFromForm(0));
  }

  clearFilters(): void {
    this.filtersForm.reset({
      name: '',
      phoneNumber: '',
      personType: '',
      role: '',
    });
    this.loadPage(0, emptyFilters());
  }

  retry(): void {
    this.errorMessage.set(null);
    this.queryRequests.next(this.activeFilters());
  }

  previousPage(): void {
    if (this.isFirstPage() || this.isLoading()) {
      return;
    }

    this.loadPage(this.currentPage() - 1);
  }

  nextPage(): void {
    if (this.isLastPage() || this.isLoading()) {
      return;
    }

    this.loadPage(this.currentPage() + 1);
  }

  openRoleChange(person: PersonAdmin, trigger: HTMLButtonElement): void {
    this.errorMessage.set(null);
    this.roleChangeErrorMessage.set(null);
    this.successMessage.set(null);
    this.lastRoleChangeButton = trigger;
    this.pendingRoleChange.set(person);
    this.selectedRole.set(null);
    this.focusFirstRoleOption(person.id);
  }

  cancelRoleChange(): void {
    if (this.isSaving()) {
      return;
    }

    this.pendingRoleChange.set(null);
    this.selectedRole.set(null);
    this.roleChangeErrorMessage.set(null);
    this.focusLastRoleChangeButton();
  }

  selectRole(role: UserRole): void {
    const person = this.pendingRoleChange();

    if (person !== null && this.isSelfAdminDemotionOption(person, role)) {
      return;
    }

    this.selectedRole.set(role);
  }

  confirmRoleChange(): void {
    const person = this.pendingRoleChange();
    const role = this.selectedRole();

    if (person === null || role === null || this.isConfirmDisabled(person)) {
      return;
    }

    this.isSaving.set(true);
    this.errorMessage.set(null);
    this.roleChangeErrorMessage.set(null);
    this.successMessage.set(null);

    this.adminUserService.updateRole(person.id, role).subscribe({
      next: () => {
        this.isSaving.set(false);
        this.pendingRoleChange.set(null);
        this.selectedRole.set(null);
        this.successMessage.set('Perfil atualizado com sucesso.');
        this.reloadCurrentPage();
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.roleChangeErrorMessage.set(roleUpdateErrorMessageFor(error));
      },
    });
  }

  personTypeLabel(personType: PersonType): string {
    return (
      this.personTypeOptions.find((option) => option.value === personType)?.label ??
      'Categoria desconhecida'
    );
  }

  roleLabel(role: UserRole): string {
    return this.roleOptions.find((option) => option.value === role)?.label ?? role;
  }

  rolesLabel(roles: readonly UserRole[]): string {
    return roles.length === 0 ? 'Sem perfil' : roles.map((role) => this.roleLabel(role)).join(', ');
  }

  formatPhone(phoneNumber: string): string {
    const digits = phoneNumber.replace(/\D/g, '');

    if (digits.length === 11) {
      return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
    }

    if (digits.length === 10) {
      return `(${digits.slice(0, 2)}) ${digits.slice(2, 6)}-${digits.slice(6)}`;
    }

    return phoneNumber;
  }

  hasActiveFilters(): boolean {
    const filters = this.activeFilters();

    return Boolean(filters.name || filters.phoneNumber || filters.personType || filters.role);
  }

  isConfirmDisabled(person: PersonAdmin): boolean {
    const role = this.selectedRole();

    return (
      this.isSaving() ||
      role === null ||
      this.hasOnlyRole(person, role) ||
      this.isSelfAdminDemotionOption(person, role)
    );
  }

  isSelfAdmin(person: PersonAdmin): boolean {
    return person.phoneNumber === this.authenticatedUsername && person.roles.includes('ROLE_ADMIN');
  }

  isSelfAdminDemotionOption(person: PersonAdmin, role: UserRole): boolean {
    return this.isSelfAdmin(person) && role === 'ROLE_OPERATOR';
  }

  isRoleChangeOpen(person: PersonAdmin): boolean {
    return this.pendingRoleChange()?.id === person.id;
  }

  roleChangePanelId(person: PersonAdmin): string {
    return `role-change-panel-${person.id}`;
  }

  roleChangeTitleId(person: PersonAdmin): string {
    return `role-change-title-${person.id}`;
  }

  private loadPage(
    page: number,
    filters: PersonAdminFilters | null = null,
    clearErrorMessage = true,
  ): void {
    const query = filters ?? { ...this.activeFilters(), page };

    if (clearErrorMessage) {
      this.errorMessage.set(null);
    }

    this.activeFilters.set(query);
    this.queryRequests.next(query);
  }

  private reloadCurrentPage(clearErrorMessage = true): void {
    this.loadPage(this.currentPage(), null, clearErrorMessage);
  }

  private createFiltersFromForm(page: number): PersonAdminFilters {
    const value = this.filtersForm.getRawValue();

    return {
      name: trimmedOrUndefined(value.name),
      phoneNumber: trimmedOrUndefined(value.phoneNumber),
      personType: value.personType || undefined,
      role: value.role || undefined,
      page,
      size: DEFAULT_PAGE_SIZE,
    };
  }

  private applyPage(page: PersonAdminPage): void {
    this.people.set(page.content);
    this.currentPage.set(page.number);
    this.totalPages.set(page.totalPages);
    this.totalElements.set(page.totalElements);
    this.isFirstPage.set(page.first);
    this.isLastPage.set(page.last);
  }

  private hasOnlyRole(person: PersonAdmin, role: UserRole): boolean {
    return person.roles.length === 1 && person.roles[0] === role;
  }

  private focusFirstRoleOption(personId: number): void {
    window.setTimeout(() => {
      this.host.nativeElement
        .querySelector<HTMLInputElement>(
          `#role-change-panel-${personId} input[type="radio"]:not(:disabled)`,
        )
        ?.focus();
    });
  }

  private focusLastRoleChangeButton(): void {
    window.setTimeout(() => {
      this.lastRoleChangeButton?.focus();
    });
  }
}

function emptyFilters(): PersonAdminFilters {
  return {
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  };
}

function trimmedOrUndefined(value: string): string | undefined {
  const trimmedValue = value.trim();

  return trimmedValue.length === 0 ? undefined : trimmedValue;
}

function listErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'Não foi possível aplicar os filtros informados.';
    }

    if (error.status === 403) {
      return 'Você não possui permissão para gerenciar usuários.';
    }
  }

  return 'Não foi possível carregar os usuários. Tente novamente.';
}

function roleUpdateErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'O perfil selecionado não é válido.';
    }

    if (error.status === 403) {
      return 'Você não possui permissão para gerenciar usuários.';
    }

    if (error.status === 404) {
      return 'A pessoa selecionada não foi encontrada.';
    }

    if (error.status === 409) {
      return conflictMessageFor(error.error);
    }
  }

  return 'Não foi possível alterar o perfil. Tente novamente.';
}

function conflictMessageFor(value: unknown): string {
  const message = extractMessage(value);

  if (message === 'Voce nao pode remover o seu proprio perfil administrativo.') {
    return 'Você não pode remover o seu próprio perfil administrativo.';
  }

  if (message === 'O ultimo administrador do sistema nao pode ter seu perfil alterado.') {
    return 'Não é possível remover o perfil do último administrador do sistema.';
  }

  return 'Não foi possível alterar o perfil devido a uma regra administrativa.';
}

function extractMessage(value: unknown): string | null {
  if (typeof value === 'string') {
    return value;
  }

  if (typeof value !== 'object' || value === null) {
    return null;
  }

  const message = (value as Record<string, unknown>)['message'];

  return typeof message === 'string' ? message : null;
}
