import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { catchError, map, of, Subject, switchMap } from 'rxjs';

import { apiErrorCode } from '../../api-error.utils';
import { AuthSessionService } from '../../auth-session.service';
import {
  MinistryCatalogItem,
  MinistrySummary,
  PersonAdmin,
  PersonAdminFilters,
  PersonAdminPage,
  PersonMinistriesResponse,
  UserRole,
} from '../admin-user.models';
import { AdminUserService } from '../admin-user.service';

const DEFAULT_PAGE_SIZE = 10;
const QUERY_PARAM_KEYS = [
  'name',
  'phoneNumber',
  'ministryId',
  'role',
  'personActive',
  'accountExists',
  'accountEnabled',
  'page',
] as const;

type BooleanFilterValue = '' | 'true' | 'false';

interface UserRoleOption {
  readonly value: UserRole;
  readonly label: string;
}

interface BooleanFilterOption {
  readonly value: BooleanFilterValue;
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
    ministryId: [''],
    role: ['' as UserRole | ''],
    personActive: ['' as BooleanFilterValue],
    accountExists: ['' as BooleanFilterValue],
    accountEnabled: ['' as BooleanFilterValue],
  });

  readonly roleOptions: readonly UserRoleOption[] = [
    { value: 'ROLE_ADMIN', label: 'Administrador' },
    { value: 'ROLE_OPERATOR', label: 'Operador' },
  ];
  readonly personActiveOptions: readonly BooleanFilterOption[] = [
    { value: '', label: 'Todos' },
    { value: 'true', label: 'Ativas' },
    { value: 'false', label: 'Inativas' },
  ];
  readonly accountExistsOptions: readonly BooleanFilterOption[] = [
    { value: '', label: 'Todas' },
    { value: 'true', label: 'Com conta' },
    { value: 'false', label: 'Sem conta' },
  ];
  readonly accountEnabledOptions: readonly BooleanFilterOption[] = [
    { value: '', label: 'Todas' },
    { value: 'true', label: 'Habilitadas' },
    { value: 'false', label: 'Desabilitadas' },
  ];

  readonly people = signal<PersonAdmin[]>([]);
  readonly ministryCatalog = signal<MinistryCatalogItem[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly ministryCatalogErrorMessage = signal<string | null>(null);
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
  readonly selectedMinistries = signal<number[]>([]);
  readonly isMinistriesLoading = signal(false);
  readonly isMinistriesLoaded = signal(false);
  readonly isSavingMinistries = signal(false);
  readonly ministriesErrorMessage = signal<string | null>(null);

  private readonly authenticatedUsername = this.authSessionService.getUsername();

  ngOnInit(): void {
    this.loadMinistryCatalog();

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

        this.selectedMinistries.set(
          dedupeMinistryIds(result.response.ministries.map((ministry) => ministry.id)),
        );
        this.isMinistriesLoaded.set(true);
        this.focusMinistriesPanel(result.personId);
      });

    this.filtersForm.controls.accountExists.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.applyAccountExistsConstraint(value));

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
      ministryId: '',
      role: '',
      personActive: '',
      accountExists: '',
      accountEnabled: '',
    });
    this.applyAccountExistsConstraint('');
    this.loadPage(0, emptyFilters());
  }

  isAccountFilterConstrained(): boolean {
    return this.filtersForm.controls.accountExists.value === 'false';
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

  toggleMinistry(ministryId: number): void {
    const current = this.selectedMinistries();

    this.selectedMinistries.set(
      current.includes(ministryId)
        ? current.filter((value) => value !== ministryId)
        : dedupeMinistryIds([...current, ministryId]),
    );
  }

  isMinistrySelected(ministryId: number): boolean {
    return this.selectedMinistries().includes(ministryId);
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

    const ministryIds = dedupeMinistryIds(this.selectedMinistries());

    this.isSavingMinistries.set(true);
    this.ministriesErrorMessage.set(null);
    this.successMessage.set(null);

    this.adminUserService.updateMinistries(person.id, ministryIds).subscribe({
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

  activeMinistryOptions(): readonly MinistryCatalogItem[] {
    return this.ministryCatalog().filter((ministry) => ministry.active);
  }

  ministriesLabel(ministries: readonly MinistrySummary[]): string {
    return ministries.length === 0
      ? 'Sem ministérios'
      : ministries.map((ministry) => ministry.name).join(', ');
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

    return (
      Boolean(filters.name) ||
      Boolean(filters.phoneNumber) ||
      filters.ministryId !== undefined ||
      Boolean(filters.role) ||
      filters.personActive !== undefined ||
      filters.accountExists !== undefined ||
      filters.accountEnabled !== undefined
    );
  }

  personStatusLabel(person: PersonAdmin): string {
    return person.personActive ? 'Ativa' : 'Inativa';
  }

  accountStatusLabel(person: PersonAdmin): string {
    if (!person.accountExists) {
      return 'Sem conta de acesso';
    }

    if (person.accountEnabled === true) {
      return 'Conta habilitada';
    }

    if (person.accountEnabled === false) {
      return 'Conta desabilitada';
    }

    return 'Status da conta indisponível';
  }

  accessProfileLabel(person: PersonAdmin): string {
    if (!person.accountExists) {
      return 'Sem conta de acesso';
    }

    if (person.roles.includes('ROLE_ADMIN')) {
      return 'Administrador';
    }

    if (person.roles.includes('ROLE_OPERATOR')) {
      return 'Operador';
    }

    return 'Perfil de acesso indisponível';
  }

  showsInactiveWithEnabledAccountHint(person: PersonAdmin): boolean {
    return !person.personActive && person.accountExists && person.accountEnabled === true;
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
    return (
      person.accountExists &&
      person.username !== null &&
      person.username === this.authenticatedUsername &&
      person.roles.includes('ROLE_ADMIN')
    );
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

  ministryCheckboxId(person: PersonAdmin, ministryId: number): string {
    return `ministry-checkbox-${person.id}-${ministryId}`;
  }

  private loadMinistryCatalog(): void {
    this.ministryCatalogErrorMessage.set(null);

    this.adminUserService
      .findMinistryCatalog()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ministries) => this.ministryCatalog.set(ministries),
        error: (error: unknown) =>
          this.ministryCatalogErrorMessage.set(ministryCatalogErrorMessageFor(error)),
      });
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
      ministryId: filters.ministryId === undefined ? null : String(filters.ministryId),
      role: filters.role ?? null,
      personActive: booleanToQueryParam(filters.personActive),
      accountExists: booleanToQueryParam(filters.accountExists),
      accountEnabled: booleanToQueryParam(filters.accountEnabled),
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
    const ministryId = parsePositiveNumberParam(queryParamMap.get('ministryId'));
    const roleParam = queryParamMap.get('role');
    let role = isUserRole(roleParam) ? roleParam : undefined;
    const personActive = parseBooleanParam(queryParamMap.get('personActive'));
    const accountExists = parseBooleanParam(queryParamMap.get('accountExists'));
    let accountEnabled = parseBooleanParam(queryParamMap.get('accountEnabled'));
    const page = validPageOrDefault(queryParamMap.get('page'));

    if (accountExists === false) {
      role = undefined;
      accountEnabled = undefined;
    }

    this.filtersForm.patchValue(
      {
        name: name ?? '',
        phoneNumber: phoneNumber ?? '',
        ministryId: ministryId === undefined ? '' : String(ministryId),
        role: role ?? '',
        personActive: booleanToFilterValue(personActive),
        accountExists: booleanToFilterValue(accountExists),
        accountEnabled: booleanToFilterValue(accountEnabled),
      },
      { emitEvent: false },
    );
    this.applyAccountExistsConstraint(booleanToFilterValue(accountExists));

    return {
      ...(name !== undefined ? { name } : {}),
      ...(phoneNumber !== undefined ? { phoneNumber } : {}),
      ...(ministryId !== undefined ? { ministryId } : {}),
      ...(role !== undefined ? { role } : {}),
      ...(personActive !== undefined ? { personActive } : {}),
      ...(accountExists !== undefined ? { accountExists } : {}),
      ...(accountEnabled !== undefined ? { accountEnabled } : {}),
      page,
      size: DEFAULT_PAGE_SIZE,
    };
  }

  private applyAccountExistsConstraint(value: BooleanFilterValue): void {
    const isWithoutAccount = value === 'false';

    if (isWithoutAccount) {
      this.filtersForm.patchValue({ role: '', accountEnabled: '' }, { emitEvent: false });
      this.filtersForm.controls.role.disable({ emitEvent: false });
      this.filtersForm.controls.accountEnabled.disable({ emitEvent: false });
      return;
    }

    this.filtersForm.controls.role.enable({ emitEvent: false });
    this.filtersForm.controls.accountEnabled.enable({ emitEvent: false });
  }

  private reloadCurrentPage(clearErrorMessage = true): void {
    this.loadPage(this.currentPage(), null, clearErrorMessage);
  }

  private createFiltersFromForm(page: number): PersonAdminFilters {
    const value = this.filtersForm.getRawValue();

    return {
      name: trimmedOrUndefined(value.name),
      phoneNumber: trimmedOrUndefined(value.phoneNumber),
      ministryId: parsePositiveNumericFilterValue(value.ministryId),
      role: value.role || undefined,
      personActive: filterValueToBoolean(value.personActive),
      accountExists: filterValueToBoolean(value.accountExists),
      accountEnabled: filterValueToBoolean(value.accountEnabled),
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

function dedupeMinistryIds(ministryIds: readonly number[]): number[] {
  return [...new Set(ministryIds)];
}

function trimmedOrUndefined(value: string): string | undefined {
  const trimmedValue = value.trim();

  return trimmedValue.length === 0 ? undefined : trimmedValue;
}

function parsePositiveNumberParam(value: string | null): number | undefined {
  if (value === null || !/^[1-9]\d*$/.test(value)) {
    return undefined;
  }

  return Number(value);
}

function parsePositiveNumericFilterValue(value: string): number | undefined {
  const trimmedValue = value.trim();
  if (trimmedValue.length === 0 || !/^[1-9]\d*$/.test(trimmedValue)) {
    return undefined;
  }

  return Number(trimmedValue);
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

function parseBooleanParam(value: string | null): boolean | undefined {
  if (value === 'true') {
    return true;
  }

  if (value === 'false') {
    return false;
  }

  return undefined;
}

function booleanToFilterValue(value: boolean | undefined): BooleanFilterValue {
  if (value === true) {
    return 'true';
  }

  if (value === false) {
    return 'false';
  }

  return '';
}

function filterValueToBoolean(value: BooleanFilterValue): boolean | undefined {
  if (value === 'true') {
    return true;
  }

  if (value === 'false') {
    return false;
  }

  return undefined;
}

function booleanToQueryParam(value: boolean | undefined): string | null {
  return value === undefined ? null : String(value);
}

function listErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 400) {
      if (apiErrorCode(error.error) === 'PERSON_ADMIN_FILTERS_INVALID') {
        return 'Os filtros de conta informados são incompatíveis. Revise os filtros e tente novamente.';
      }

      return 'Não foi possível aplicar os filtros informados.';
    }

    if (error.status === 403) {
      return 'Você não possui permissão para gerenciar usuários.';
    }

    if (error.status === 404) {
      return 'O ministério informado no filtro não foi encontrado.';
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
      return roleConflictMessageFor(apiErrorCode(error.error));
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

function ministryCatalogErrorMessageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse && error.status === 403) {
    return 'Você não possui permissão para consultar o catálogo de ministérios.';
  }

  return 'Não foi possível carregar o catálogo de ministérios.';
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
      return 'A pessoa selecionada ou o ministério informado não foi encontrado.';
    }

    if (error.status === 409) {
      const errorCode = apiErrorCode(error.error);

      if (errorCode === 'MINISTRY_INACTIVE') {
        return 'Ministério inativo não pode ser associado ou reativado para uma pessoa.';
      }

      return 'Não é possível remover um ministério vinculado a uma escala.';
    }

    if (error.status === 422) {
      return 'O conjunto de ministérios informado não é válido.';
    }
  }

  return 'Não foi possível atualizar os ministérios. Tente novamente.';
}

function roleConflictMessageFor(errorCode: string | null): string {
  switch (errorCode) {
    case 'SELF_ADMIN_DEMOTION_NOT_ALLOWED':
      return 'Você não pode remover o seu próprio perfil administrativo.';
    case 'LAST_ACTIVE_ADMIN_REQUIRED':
      return 'Não é possível remover o perfil do último administrador efetivo do sistema.';
    case 'USER_ACCOUNT_NOT_FOUND':
      return 'Esta pessoa não possui conta de acesso.';
    case 'USER_ACCOUNT_ROLE_INVALID':
      return 'O perfil de acesso configurado para esta conta é inválido.';
    default:
      return 'Não foi possível alterar o perfil devido a uma regra administrativa.';
  }
}
