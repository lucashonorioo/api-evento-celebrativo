import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';

import {
  EventAssignmentAuditQuery,
  EventAssignmentAuditResponse,
} from '../event-assignment-audit.models';
import { EventAssignmentAuditService } from '../event-assignment-audit.service';
import { EventAssignmentAuditPageComponent } from './event-assignment-audit-page.component';

describe('EventAssignmentAuditPageComponent', () => {
  let fixture: ComponentFixture<EventAssignmentAuditPageComponent>;
  let component: EventAssignmentAuditPageComponent;
  let auditService: jasmine.SpyObj<EventAssignmentAuditService>;

  async function setup(response: EventAssignmentAuditResponse = createResponse()): Promise<void> {
    auditService = jasmine.createSpyObj<EventAssignmentAuditService>('EventAssignmentAuditService', [
      'auditConsistency',
    ]);
    auditService.auditConsistency.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      imports: [EventAssignmentAuditPageComponent],
      providers: [{ provide: EventAssignmentAuditService, useValue: auditService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EventAssignmentAuditPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should render the read-only audit header without running the audit automatically', async () => {
    await setup();

    const text = textContent();

    expect(text).toContain('Auditoria de consistência das escalas');
    expect(text).toContain(
      'Compara os vínculos legados dos eventos com as atribuições paralelas',
    );
    expect(text).toContain('A auditoria é somente leitura');
    expect(auditService.auditConsistency).not.toHaveBeenCalled();
  });

  it('should execute the audit with valid filters and page zero', async () => {
    await setup();

    component.filtersForm.patchValue({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      eventId: '15',
      size: 50,
      includeDetails: false,
    });
    component.executeAudit();
    fixture.detectChanges();

    expect(auditService.auditConsistency).toHaveBeenCalledOnceWith({
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      eventId: 15,
      page: 0,
      size: 50,
      includeDetails: false,
    });
  });

  it('should not send empty optional filters', async () => {
    await setup();

    component.executeAudit();

    expect(auditService.auditConsistency).toHaveBeenCalledOnceWith({
      page: 0,
      size: 20,
      includeDetails: true,
    });
  });

  it('should block invalid filters before requesting the API', async () => {
    await setup();

    component.filtersForm.patchValue({
      startDate: '2026-08-01',
      endDate: '2026-07-31',
      eventId: '0',
      size: 101,
    });
    component.executeAudit();
    fixture.detectChanges();

    expect(auditService.auditConsistency).not.toHaveBeenCalled();
    expect(textContent()).toContain('A data inicial não pode ser posterior');
    expect(textContent()).toContain('ID de evento inteiro e positivo');
    expect(textContent()).toContain('tamanho entre 1 e 100');
  });

  it('should prevent duplicate audit requests while loading', async () => {
    const pendingRequest = new Subject<EventAssignmentAuditResponse>();
    await setup();
    auditService.auditConsistency.and.returnValue(pendingRequest);

    component.executeAudit();
    component.executeAudit();

    expect(auditService.auditConsistency).toHaveBeenCalledTimes(1);

    pendingRequest.next(createResponse());
    pendingRequest.complete();
  });

  it('should render summary metrics and total elements separately', async () => {
    await setup(createResponse({ totalElements: 12 }));

    component.executeAudit();
    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Resumo da página auditada');
    expect(text).toContain('Eventos auditados');
    expect(text).toContain('Eventos inconsistentes');
    expect(text).toContain('Total de problemas');
    expect(text).toContain('Ausentes no paralelo');
    expect(text).toContain('Total de eventos encontrados pelos filtros: 12');
  });

  it('should describe consistent and empty pages using summary metadata', async () => {
    await setup(
      createResponse({
        summary: createSummary({ eventsChecked: 2, consistentEvents: 2, inconsistentEvents: 0 }),
        events: [],
      }),
    );

    component.executeAudit();
    fixture.detectChanges();

    expect(textContent()).toContain('Página consistente');

    auditService.auditConsistency.and.returnValue(
      of(
        createResponse({
          summary: createSummary({ eventsChecked: 0, consistentEvents: 0, inconsistentEvents: 0 }),
          empty: true,
          numberOfElements: 0,
          totalElements: 0,
          totalPages: 0,
          events: [],
        }),
      ),
    );
    component.executeAudit();
    fixture.detectChanges();

    expect(textContent()).toContain('Nenhum evento encontrado');
  });

  it('should hide details when includeDetails is false', async () => {
    await setup(createResponse({ events: undefined }));

    component.filtersForm.patchValue({ includeDetails: false });
    component.executeAudit();
    fixture.detectChanges();

    expect(textContent()).toContain('Os detalhes foram ocultados para esta auditoria');
    expect(fixture.nativeElement.querySelector('.audit__details')).toBeNull();
  });

  it('should expand event issues with translated labels and null fallbacks', async () => {
    await setup();

    component.executeAudit();
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '[aria-controls="event-assignment-audit-event-10"]',
    ) as HTMLButtonElement;

    expect(button.getAttribute('aria-expanded')).toBe('false');

    button.click();
    fixture.detectChanges();

    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(textContent()).toContain('Vínculo ausente na estrutura paralela');
    expect(textContent()).toContain('ID da pessoa');
    expect(textContent()).toContain('20');
    expect(textContent()).toContain('Leitor');
    expect(textContent()).toContain('Não aplicável');
  });

  it('should request next and previous pages preserving executed filters', async () => {
    await setup(createResponse({ page: 0, totalPages: 3 }));

    component.filtersForm.patchValue({ startDate: '2026-07-01', size: 10 });
    component.executeAudit();
    fixture.detectChanges();
    auditService.auditConsistency.calls.reset();
    auditService.auditConsistency.and.returnValue(of(createResponse({ page: 1, totalPages: 3 })));

    component.nextPage();
    fixture.detectChanges();

    expect(auditService.auditConsistency.calls.mostRecent().args[0]).toEqual({
      startDate: '2026-07-01',
      page: 1,
      size: 10,
      includeDetails: true,
    });

    auditService.auditConsistency.and.returnValue(of(createResponse({ page: 0, totalPages: 3 })));
    component.previousPage();

    expect(auditService.auditConsistency.calls.mostRecent().args[0]).toEqual({
      startDate: '2026-07-01',
      page: 0,
      size: 10,
      includeDetails: true,
    });
  });

  it('should clear filters and results without requesting the API', async () => {
    await setup();

    component.filtersForm.patchValue({ startDate: '2026-07-01', eventId: '10' });
    component.executeAudit();
    fixture.detectChanges();
    auditService.auditConsistency.calls.reset();

    component.clearFilters();
    fixture.detectChanges();

    expect(component.filtersForm.getRawValue()).toEqual({
      startDate: '',
      endDate: '',
      eventId: '',
      size: 20,
      includeDetails: true,
    });
    expect(auditService.auditConsistency).not.toHaveBeenCalled();
    expect(textContent()).toContain('Informe os filtros desejados');
  });

  it('should show friendly errors and retry the last valid query', async () => {
    await setup();
    auditService.auditConsistency.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 404 })),
      of(createResponse()),
    );

    component.filtersForm.patchValue({ eventId: '999' });
    component.executeAudit();
    fixture.detectChanges();

    expect(textContent()).toContain('O evento informado não foi encontrado');

    const retryButton = fixture.nativeElement.querySelector(
      '.audit__feedback--error button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(auditService.auditConsistency).toHaveBeenCalledTimes(2);
    expect(auditService.auditConsistency.calls.mostRecent().args[0]).toEqual({
      eventId: 999,
      page: 0,
      size: 20,
      includeDetails: true,
    });
    expect(textContent()).toContain('Resumo da página auditada');
  });

  it('should map forbidden, validation and generic errors to friendly messages', async () => {
    await setup();

    expect(errorMessageFromStatus(403)).toContain('permissão');
    expect(errorMessageFromStatus(422)).toContain('filtros informados');
    expect(errorMessageFromStatus(500)).toContain('Tente novamente');
  });

  function errorMessageFromStatus(status: number): string {
    auditService.auditConsistency.and.returnValue(
      throwError(() => new HttpErrorResponse({ status })),
    );

    component.executeAudit();
    fixture.detectChanges();
    auditService.auditConsistency.calls.reset();

    return textContent();
  }

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }
});

function createSummary(
  overrides: Partial<EventAssignmentAuditResponse['summary']> = {},
): EventAssignmentAuditResponse['summary'] {
  return {
    eventsChecked: 1,
    consistentEvents: 0,
    inconsistentEvents: 1,
    legacyParticipants: 2,
    parallelAssignments: 1,
    totalIssues: 1,
    missingParallelAssignments: 1,
    extraParallelAssignments: 0,
    assignmentTypeMismatches: 0,
    duplicateParallelAssignments: 0,
    multiplePriests: 0,
    unknownLegacyPersonTypes: 0,
    ...overrides,
  };
}

function createResponse(
  overrides: Partial<EventAssignmentAuditResponse> = {},
): EventAssignmentAuditResponse {
  return {
    summary: createSummary(),
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    numberOfElements: 1,
    empty: false,
    events: [
      {
        eventId: 10,
        consistent: false,
        legacyParticipantCount: 2,
        parallelAssignmentCount: 1,
        issueCount: 1,
        issues: [
          {
            issueType: 'MISSING_PARALLEL_ASSIGNMENT',
            eventId: 10,
            personId: 20,
            legacyType: 'READER',
            parallelType: null,
          },
        ],
      },
    ],
    ...overrides,
  };
}
