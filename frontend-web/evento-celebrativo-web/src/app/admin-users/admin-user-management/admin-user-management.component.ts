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
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import {
  MinistryType,
  PersonAdmin,
  PersonAdminFilters,
  PersonAdminPage,
  PersonMinistriesResponse,
  UserRole,
} from '../admin-user.models';
import { AdminUserService } from '../admin-user.service';

const DEFAULT_PAGE_SIZE = 10;
const QUERY_PARAM_KEYS = ['name', 'phoneNumber', 'ministry', 'role', 'page'] as const;

interface MinistryTypeOption {
  readonly value: MinistryType;
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

type MinistriesQueryResult =
  | {
      readonly type: 'success';
      readonly personId: number;
      readonly response: PersonMinistriesResponse;
    }
  | {
      readonly type: 'error';
      readonly personId: number;
      readonly error: unknown;
    };

@Component({
  selector: 'app-admin-user-management',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './admin-user-management.component.html',
  styleUrl: './admin-user-management.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminUserManagementComponent implements OnInit {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly adminUserService = inject(AdminUserService);
  private readonly authSessionService = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly queryRequests = new Subject<PersonAdminFilters>();
  private readonly ministriesRequests = new Subject<number>();
  private lastRoleChangeButton: HTMLButtonElement | null = null;
  private lastMinistriesButton: HTMLButtonElement | null = null;

  readonly filtersForm = this.formBuilder.group({
    name: [''],
    phoneNumber: [''],
    ministry: ['' as MinistryType | ''],
    role: ['' as UserRole | ''],
  });

  readonly ministryTypeOptions: readonly MinistryTypeOption[] = [
    { value: 'PRIEST', label: 'Padre' },
    { value: 'READER', label: 'Leitor' },
    { value: 'COMMENTATOR', label: 'Comentarista' },
    { value: 'MINISTER_OF_THE_WORD', label: 'Ministro da Palavra' },
    { value: 'EUCHARISTIC_MINISTER', label: 'Ministro da Eucaristia' },
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

  readonly pendingMinistriesChange = signal<PersonAdmin | null>(null);
  readonly selectedMinistries = signal<MinistryType[]>([]);
  readonly isMinistriesLoading = signal(false);
  readonly isMinistriesLoaded = signal(false);
  readonly isSavingMinistries = signal(false);
  readonly ministriesErrorMessage = signal<string | null>(null);

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

        if (result.page.number !== result.query.page) {
          const correctedFilters = { ...result.query, page: result.page.number };
          this.activeFilters.set(correctedFilters);
          this.syncQueryParams(correctedFilters);
        }
      });

    this.ministriesRequests
      .pipe(
        switchMap((personId) =>
          this.adminUserService.findMinistries(personId).pipe(
            map((response): MinistriesQueryResult => ({ type: 'success', personId, response })),
            catchError((error: unknown) =>
              of({ type: 'error', personId, error } satisfies MinistriesQueryResult),
            ),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        const current = this.pendingMinistriesChange();

        if (current === null || current.id !== result.personId) {
          return;
        }

        this.isMinistriesLoading.set(false);

        if (result.type === 'error') {
          this.ministriesErrorMessage.set(ministriesLoadErrorMessageFor(result.error));
          this.focusMinistriesPanel(result.personId);
          return;
        }

        this.selectedMinistries.set(dedupeMinistries(result.response.ministries));
        this.isMinistriesLoaded.set(true);
        this.focusMinistriesPanel(result.personId);
      });

    const restoredFilters = this.restoreFiltersFromQueryParams();
    this.loadPage(restoredFilters.page, restoredFilters);
  }

  applyFilters(): void {
    const rawValue = this.filtersForm.getRawValue();

    this.filtersForm.patchValue(
      { name: rawValue.name.trim(), phoneNumber: rawValue.phoneNumber.trim() },
      { emitEvent: false },
    );

    this.loadPage(0, this.createFiltersFromForm(0));
  }

  clearFilters(): void {
    this.filtersForm.reset({
      name: '',
      phoneNumber: '',
      ministry: '',
      role: '',
    });
    this.loadPage(0, emptyFilters());
  }

  retry(): void {
    this.errorMessage.set(null);
    this.syncQueryParams(this.activeFilters());
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
    this.resetMinistriesPanelState();
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

    this.resetRolePanelState();
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
        this.resetRolePanelState();
        this.successMessage.set('Perfil atualizado com sucesso.');
        this.reloadCurrentPage();
      },
      error: (error: unknown) => {
        this.isSaving.set(false);
        this.roleChangeErrorMessage.set(roleUpdateErrorMessageFor(error));
      },
    });
  }

  openMinistriesChange(person: PersonAdmin, trigger: HTMLButtonElement): void {
    this.resetRolePanelState();
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.lastMinistriesButton = trigger;
    this.pendingMinistriesChange.set(person);
    this.selectedMinistries.set([]);
    this.ministriesErrorMessage.set(null);
    this.isMinistriesLoaded.set(false);
    this.isMinistriesLoading.set(true);
    this.ministriesRequests.next(person.id);
  }

  cancelMinistriesChange(): void {
    if (this.isSavingMinistries()) {
      return;
    }

    this.resetMinistriesPanelState();
    this.focusLastMinistriesButton();
  }

  toggleMinistry(ministry: MinistryType): void {
    const current = this.selectedMinistries();

    this.selectedMinistries.set(
      current.includes(ministry)
        ? current.filter((value) => value !== ministry)
        : dedupeMinistries([...current, ministry]),
    );
  }

  isMinistrySelected(ministry: MinistryType): boolean {
    return this.selectedMinistries().includes(ministry);
  }

  confirmMinistriesChange(): void {
    const person = this.pendingMinistriesChange();

    if (
      person === null ||
      this.isSavingMinistries() ||
      this.isMinistriesLoading() ||
      !this.isMinistriesLoaded()
    ) {
      return;
    }

    const ministries = dedupeMinistries(this.selectedMinistries());

    this.isSavingMinistries.set(true);
    this.ministriesErrorMessage.set(null);
    this.successMessage.set(null);

    this.adminUserService.updateMinistries(person.id, ministries).subscribe({
      next: () => {
        this.isSavingMinistries.set(false);
        this.resetMinistriesPanelState();
        this.successMessage.set('Ministérios atualizados com sucesso.');
        this.reloadCurrentPage();
      },
      error: (error: unknown) => {
        this.isSavingMinistries.set(false);
        this.ministriesErrorMessage.set(ministriesUpdateErrorMessageFor(error));
      },
    });
  }

  ministryLabel(ministry: MinistryType): string {
    return (
      this.ministryTypeOptions.find((option) => option.value === ministry)?.label ?? ministry
    );
  }

  ministriesLabel(ministries: readonly MinistryType[]): string {
    return ministries.length === 0
      ? 'Sem ministérios'
      : ministries.map((ministry) => this.ministryLabel(ministry)).join(', ');
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

    return Boolean(filters.name || filters.phoneNumber || filters.ministry || filters.role);
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

  isMinistriesChangeOpen(person: PersonAdmin): boolean {
    return this.pendingMinistriesChange()?.id === person.id;
  }

  ministriesPanelId(person: PersonAdmin): string {
    return `ministries-panel-${person.id}`;
  }

  ministriesTitleId(person: PersonAdmin): string {
    return `ministries-title-${person.id}`;
  }

  ministryCheckboxId(person: PersonAdmin, ministry: MinistryType): string {
    return `ministry-checkbox-${person.id}-${ministry}`;
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
    this.syncQueryParams(query);
    this.queryRequests.next(query);
  }

  private syncQueryParams(filters: PersonAdminFilters): void {
    const desired: Params = {
      name: filters.name ?? null,
      phoneNumber: filters.phoneNumber ?? null,
      ministry: filters.ministry ?? null,
      role: filters.role ?? null,
      page: filters.page === 0 ? null : String(filters.page),
    };

    const current = this.activatedRoute.snapshot.queryParamMap;
    const hasChanges = QUERY_PARAM_KEYS.some((key) => current.get(key) !== desired[key]);

    if (!hasChanges) {
      return;
    }

    void this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: desired,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private restoreFiltersFromQueryParams(): PersonAdminFilters {
    const queryParamMap = this.activatedRoute.snapshot.queryParamMap;
    const name = trimmedOrUndefined(queryParamMap.get('name') ?? '');
    const phoneNumber = trimmedOrUndefined(queryParamMap.get('phoneNumber') ?? '');
    const ministryParam = queryParamMap.get('ministry');
    const roleParam = queryParamMap.get('role');
    const ministry = isMinistryType(ministryParam) ? ministryParam : undefined;
    const role = isUserRole(roleParam) ? roleParam : undefined;
    const page = validPageOrDefault(queryParamMap.get('page'));

    this.filtersForm.patchValue(
      {
        name: name ?? '',
        phoneNumber: phoneNumber ?? '',
        ministry: ministry ?? '',
        role: role ?? '',
      },
      { emitEvent: false },
    );

    return {
      ...(name !== undefined ? { name } : {}),
      ...(phoneNumber !== undefined ? { phoneNumber } : {}),
      ...(ministry !== undefined ? { ministry } : {}),
      ...(role !== undefined ? { role } : {}),
      page,
      size: DEFAULT_PAGE_SIZE,
    };
  }

  private reloadCurrentPage(clearErrorMessage = true): void {
    this.loadPage(this.currentPage(), null, clearErrorMessage);
  }

  private createFiltersFromForm(page: number): PersonAdminFilters {
    const value = this.filtersForm.getRawValue();

    return {
      name: trimmedOrUndefined(value.name),
      phoneNumber: trimmedOrUndefined(value.phoneNumber),
      ministry: value.ministry || undefined,
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

  private resetRolePanelState(): void {
    this.pendingRoleChange.set(null);
    this.selectedRole.set(null);
    this.roleChangeErrorMessage.set(null);
  }

  private resetMinistriesPanelState(): void {
    this.pendingMinistriesChange.set(null);
    this.selectedMinistries.set([]);
    this.ministriesErrorMessage.set(null);
    this.isMinistriesLoading.set(false);
    this.isMinistriesLoaded.set(false);
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

  private focusMinistriesPanel(personId: number): void {
    window.setTimeout(() => {
      const firstCheckbox = this.host.nativeElement.querySelector<HTMLInputElement>(
        `#ministries-panel-${personId} input[type="checkbox"]:not(:disabled)`,
      );

      if (firstCheckbox) {
        firstCheckbox.focus();
        return;
      }

      this.host.nativeElement
        .querySelector<HTMLElement>(`#ministries-title-${personId}`)
        ?.focus();
    });
  }

  private focusLastMinistriesButton(): void {
    window.setTimeout(() => {
      this.lastMinistriesButton?.focus();
    });
  }
}

function emptyFilters(): PersonAdminFilters {
  return {
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  };
}

function dedupeMinistries(ministries: readonly MinistryType[]): MinistryType[] {
  return [...new Set(ministries)];
}

function trimmedOrUndefined(value: string): string | undefined {
  const trimmedValue = value.trim();

  return trimmedValue.length === 0 ? undefined : trimmedValue;
}

function isMinistryType(value: string | null): value is MinistryType {
  return (
    value === 'PRIEST' ||
    value === 'READER' ||
    value === 'COMMENTATOR' ||
    value === 'MINISTER_OF_THE_WORD' ||
    value === 'EUCHARISTIC_MINISTER'
  );
}

function isUserRole(value: string | null): value is UserRole {
  return value === 'ROLE_ADMIN' || value === 'ROLE_OPERATOR';
}

function validPageOrDefault(value: string | null): number {
  if (value === null || !/^\d+$/.test(value)) {
    return 0;
  }

  return Number(value);
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

function ministriesLoadErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 403) {
      return 'Você não possui permissão para gerenciar usuários.';
    }

    if (error.status === 404) {
      return 'A pessoa selecionada não foi encontrada.';
    }
  }

  return 'Não foi possível carregar os ministérios. Tente novamente.';
}

function ministriesUpdateErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      return 'O ministério informado não é válido.';
    }

    if (error.status === 403) {
      return 'Você não possui permissão para gerenciar usuários.';
    }

    if (error.status === 404) {
      return 'A pessoa selecionada não foi encontrada.';
    }

    if (error.status === 409) {
      return 'Não é possível remover um ministério vinculado a uma escala.';
    }

    if (error.status === 422) {
      return 'O conjunto de ministérios informado não é válido.';
    }
  }

  return 'Não foi possível atualizar os ministérios. Tente novamente.';
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

  const message = (value as Record<string, unknown>)['error'];

  return typeof message === 'string' ? message : null;
}
