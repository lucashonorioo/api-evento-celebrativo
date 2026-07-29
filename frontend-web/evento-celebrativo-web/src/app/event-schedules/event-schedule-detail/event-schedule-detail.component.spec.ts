import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import { AuthSessionService } from '../../auth-session.service';
import {
  EventScheduleDetailResponse,
  EventScheduleParticipationDetailResponse,
  EventScheduleParticipationPersonSummary,
  EventSchedulePersonSummary,
} from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleDetailComponent } from './event-schedule-detail.component';

describe('EventScheduleDetailComponent', () => {
  let component: EventScheduleDetailComponent;
  let fixture: ComponentFixture<EventScheduleDetailComponent>;
  let authSessionService: jasmine.SpyObj<AuthSessionService>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;

  async function setup(
    routeId: string | null = '1',
    response: EventScheduleDetailResponse = createDetail(),
    queryParams: Record<string, string> = {},
    isAdmin = false,
    routeConfigPath = 'escalas/eventos/:id',
    participationResponse: EventScheduleParticipationDetailResponse = toParticipationDetail(response),
  ): Promise<void> {
    authSessionService = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
      'hasAuthority',
    ]);
    authSessionService.hasAuthority.and.returnValue(isAdmin);
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'findByEventId',
      'findParticipationByEventId',
    ]);
    eventScheduleService.findByEventId.and.returnValue(of(response));
    eventScheduleService.findParticipationByEventId.and.returnValue(of(participationResponse));

    await TestBed.configureTestingModule({
      imports: [EventScheduleDetailComponent],
      providers: [
        provideRouter([]),
        { provide: AuthSessionService, useValue: authSessionService },
        { provide: EventScheduleService, useValue: eventScheduleService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap(routeId === null ? {} : { id: routeId }),
              queryParamMap: convertToParamMap(queryParams),
              routeConfig: { path: routeConfigPath },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventScheduleDetailComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create and render the page header and return link', async () => {
    await setup();

    fixture.detectChanges();

    const returnLink = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;
    const text = textContent();

    expect(component).toBeTruthy();
    expect(text).toContain('Escala do evento');
    expect(text).toContain('Consulte todas as pessoas escaladas');
    expect(returnLink.textContent).toContain('Voltar para Escalas');
    expect(returnLink.getAttribute('href')).toContain('/app/escalas');
  });

  it('should call the service when route id is valid', async () => {
    await setup('15');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).toHaveBeenCalledOnceWith(15);
  });

  it('should not call the service when route id is missing', async () => {
    await setup(null);

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    expect(textContent()).toContain('Nao foi possivel identificar o evento solicitado');
  });

  it('should not call the service when route id is not numeric', async () => {
    await setup('abc');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    expect(textContent()).toContain('Nao foi possivel identificar o evento solicitado');
  });

  it('should not call the service when route id is empty', async () => {
    await setup('');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    expect(textContent()).toContain('Nao foi possivel identificar o evento solicitado');
  });

  it('should not call the service when route id is zero', async () => {
    await setup('0');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
  });

  it('should not call the service when route id is negative', async () => {
    await setup('-1');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
  });

  it('should not call the service when route id is decimal', async () => {
    await setup('1.5');

    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
  });

  it('should render loading while the request is pending', async () => {
    const pendingRequest = new Subject<EventScheduleDetailResponse>();
    await setup();
    eventScheduleService.findByEventId.and.returnValue(pendingRequest);

    fixture.detectChanges();

    expect(component.isLoading()).toBeTrue();
    expect(textContent()).toContain('Carregando escala...');

    pendingRequest.next(createDetail());
    pendingRequest.complete();
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
  });

  it('should finish loading after an error', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValue(
      throwError(() => new Error('Failed to load schedule')),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(component.isLoading()).toBeFalse();
    expect(textContent()).toContain('Nao foi possivel carregar a escala');
  });

  it('should render the full schedule detail', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Missa de Domingo da manha');
    expect(text).toContain('Missa');
    expect(text).toContain('13/07/2025');
    expect(text).toContain('10:00');
    expect(text).toContain('Igreja Matriz Nossa Senhora do Rosario');
    expect(text).toContain('Padre Miguel');
    expect(text).toContain('Alice Lima');
    expect(text).toContain('Arthur Costa');
    expect(text).toContain('Luana Odinson');
    expect(text).toContain('Davi Gomes');
    expect(text).toContain('Mariana Ferraz');
    expect(text).not.toContain('eventId');
    expect(text).not.toContain('personId');
    expect(text).not.toContain('locationId');
    expect(text).not.toContain('ROLE_ADMIN');
    expect(text).not.toContain('telefone');
  });

  it('should render the same person as a distinct entry in both the reader and commentator sections', async () => {
    await setup(
      '1',
      createDetail({
        readers: [{ id: 10, name: 'Marcos Pereira' }],
        commentators: [{ id: 10, name: 'Marcos Pereira' }],
      }),
    );

    fixture.detectChanges();

    const readerSection = sectionByTitle('Leitores');
    const commentatorSection = sectionByTitle('Comentaristas');

    expect(readerSection).not.toBe(commentatorSection);
    expect(namesIn(readerSection)).toEqual(['Marcos Pereira']);
    expect(namesIn(commentatorSection)).toEqual(['Marcos Pereira']);
  });

  it('should render celebration when the event is not a mass', async () => {
    await setup('1', createDetail({ massOrCelebration: false }));

    fixture.detectChanges();

    expect(textContent()).toContain('Celebracao');
  });

  it('should render empty messages for nullable local, nullable priest and empty lists', async () => {
    await setup(
      '1',
      createDetail({
        location: null,
        priest: null,
        readers: [],
        commentators: [],
        ministersOfTheWord: [],
        eucharisticMinisters: [],
      }),
    );

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Local nao informado');
    expect(text).toContain('Nenhum padre informado');
    expect(text).toContain('Nenhum leitor escalado');
    expect(text).toContain('Nenhum comentarista escalado');
    expect(text).toContain('Nenhum ministro da Palavra escalado');
    expect(text).toContain('Nenhum ministro da Eucaristia escalado');
  });

  it('should show not found message for 404 errors', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            statusText: 'Not Found',
          }),
      ),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('A escala do evento solicitado nao foi encontrada');
  });

  it('should show permission message for 403 errors', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 403,
            statusText: 'Forbidden',
          }),
      ),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('Voce nao possui permissao para consultar esta escala');
  });

  it('should show generic message for 401 and let the interceptor handle session behavior', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
          }),
      ),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    expect(textContent()).toContain('Nao foi possivel carregar a escala');
  });

  it('should retry the same event id after a generic error', async () => {
    await setup();
    eventScheduleService.findByEventId.and.returnValues(
      throwError(() => new Error('Failed to load schedule')),
      of(createDetail()),
    );

    fixture.detectChanges();
    fixture.detectChanges();

    const retryButton = fixture.nativeElement.querySelector(
      '.event-schedule-detail__button',
    ) as HTMLButtonElement;
    retryButton.click();
    fixture.detectChanges();

    expect(eventScheduleService.findByEventId).toHaveBeenCalledTimes(2);
    expect(eventScheduleService.findByEventId.calls.argsFor(1)).toEqual([1]);
    expect(textContent()).toContain('Missa de Domingo da manha');
  });

  it('should preserve valid query params in the return link', async () => {
    await setup('1', createDetail(), {
      type: 'READER',
      month: '2026-07',
      includeUnassigned: 'false',
      page: '2',
    });

    fixture.detectChanges();

    const returnLink = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;
    const href = returnLink.getAttribute('href') ?? '';

    expect(href).toContain('/app/escalas');
    expect(href).toContain('type=READER');
    expect(href).toContain('month=2026-07');
    expect(href).toContain('includeUnassigned=false');
    expect(href).toContain('page=2');
  });

  it('should render edit action for administrators preserving query params', async () => {
    await setup(
      '1',
      createDetail(),
      {
        type: 'READER',
        month: '2026-07',
        includeUnassigned: 'false',
        page: '2',
      },
      true,
    );

    fixture.detectChanges();

    const links = Array.from(
      fixture.nativeElement.querySelectorAll('.page-action'),
    ) as HTMLAnchorElement[];
    const editLink = links.find((link) => link.textContent?.includes('Editar escala')) as
      | HTMLAnchorElement
      | undefined;
    const href = editLink?.getAttribute('href') ?? '';

    expect(authSessionService.hasAuthority).toHaveBeenCalledWith('ROLE_ADMIN');
    expect(editLink).toBeDefined();
    expect(href).toContain('/app/admin/escalas/eventos/1/editar');
    expect(href).toContain('type=READER');
    expect(href).toContain('month=2026-07');
    expect(href).toContain('includeUnassigned=false');
    expect(href).toContain('page=2');
  });

  it('should hide edit action for operators while keeping the detail visible', async () => {
    await setup('1', createDetail(), {}, false);

    fixture.detectChanges();

    const text = textContent();

    expect(text).toContain('Missa de Domingo da manha');
    expect(text).not.toContain('Editar escala');
  });

  it('should ignore invalid query params in the return link', async () => {
    await setup('1', createDetail(), {
      type: 'INVALID',
      month: '2026',
      includeUnassigned: 'maybe',
      page: '-1',
    });

    fixture.detectChanges();

    const returnLink = fixture.nativeElement.querySelector('.page-action') as HTMLAnchorElement;
    const href = returnLink.getAttribute('href') ?? '';

    expect(href).toBe('/app/escalas');
  });

  it('should not render administrative actions', async () => {
    await setup();

    fixture.detectChanges();

    const text = textContent();

    expect(text).not.toContain('Editar');
    expect(text).not.toContain('Salvar');
    expect(text).not.toContain('Excluir');
    expect(text).not.toContain('Alterar');
    expect(text).not.toContain('Adicionar participante');
    expect(text).not.toContain('Remover participante');
    expect(text).not.toContain('Criar evento');
  });

  describe('administrative participation', () => {
    function participationSection(): HTMLElement | null {
      return fixture.nativeElement.querySelector('.participation-summary');
    }

    function statByLabel(label: string): string | undefined {
      const items = Array.from(
        (fixture.nativeElement as HTMLElement).querySelectorAll('.participation-summary__stats > div'),
      );
      const item = items.find((div) => div.querySelector('dt')?.textContent?.trim() === label);

      return item?.querySelector('dd')?.textContent?.trim();
    }

    it('should use the administrative endpoint for ROLE_ADMIN', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      expect(eventScheduleService.findParticipationByEventId).toHaveBeenCalledOnceWith(1);
    });

    it('should not call the normal endpoint for ROLE_ADMIN', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    });

    it('should use the normal endpoint for ROLE_OPERATOR', async () => {
      await setup('1', createDetail(), {}, false);

      fixture.detectChanges();

      expect(eventScheduleService.findByEventId).toHaveBeenCalledOnceWith(1);
    });

    it('should not call the administrative endpoint for ROLE_OPERATOR', async () => {
      await setup('1', createDetail(), {}, false);

      fixture.detectChanges();

      expect(eventScheduleService.findParticipationByEventId).not.toHaveBeenCalled();
    });

    it('should show the participation section for ROLE_ADMIN', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      expect(textContent()).toContain('Situação das participações');
    });

    it('should not show the participation section for ROLE_OPERATOR', async () => {
      await setup('1', createDetail(), {}, false);

      fixture.detectChanges();

      expect(textContent()).not.toContain('Situação das participações');
    });

    it('should not show status, reason or respondedAt text for ROLE_OPERATOR', async () => {
      await setup('1', createDetail(), {}, false);

      fixture.detectChanges();

      const text = textContent();

      expect(text).not.toContain('Aguardando resposta');
      expect(text).not.toContain('Participação confirmada');
      expect(text).not.toContain('Não participará');
      expect(text).not.toContain('Motivo:');
      expect(text).not.toContain('Respondido em');
      expect(text).not.toContain('Atualizar participações');
    });

    it('should translate PENDING', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'PENDING',
              declineReason: null,
              respondedAt: null,
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Aguardando resposta');
    });

    it('should translate CONFIRMED', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Participação confirmada');
    });

    it('should translate DECLINED', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 5,
              name: 'Arthur Costa',
              participationStatus: 'DECLINED',
              declineReason: 'Estarei fora da cidade.',
              respondedAt: '2026-07-30T18:21:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Não participará');
    });

    it('should show the decline reason', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 5,
              name: 'Arthur Costa',
              participationStatus: 'DECLINED',
              declineReason: 'Estarei fora da cidade.',
              respondedAt: '2026-07-30T18:21:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Motivo: Estarei fora da cidade.');
    });

    it('should not create an empty reason line when declineReason is null', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 5,
              name: 'Arthur Costa',
              participationStatus: 'DECLINED',
              declineReason: null,
              respondedAt: '2026-07-30T18:21:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).not.toContain('Motivo:');
    });

    it('should format respondedAt in local time', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('30/07/2026');
      expect(sectionByTitle('Leitores').textContent).toContain('18:20');
    });

    it('should never show a decline reason for CONFIRMED even with an inconsistent fixture', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'CONFIRMED',
              declineReason: 'Motivo inconsistente',
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).not.toContain('Motivo:');
    });

    it('should not show a date for PENDING', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'PENDING',
              declineReason: null,
              respondedAt: null,
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).not.toContain('Respondido em');
    });

    it('should not show a date for PENDING even with an inconsistent respondedAt', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'PENDING',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).not.toContain('Respondido em');
    });

    it('should not crash with an invalid respondedAt string', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: 'not-a-date',
            },
          ],
        }),
      );

      expect(() => fixture.detectChanges()).not.toThrow();
    });

    it('should show the total in the summary', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('6');
    });

    it('should show confirmed count in the summary', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 4,
              name: 'Alice Lima',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
            {
              id: 5,
              name: 'Arthur Costa',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Confirmados')).toBe('2');
    });

    it('should show pending count in the summary', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      expect(statByLabel('Aguardando resposta')).toBe('6');
    });

    it('should show declined count in the summary', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 5,
              name: 'Arthur Costa',
              participationStatus: 'DECLINED',
              declineReason: 'Estarei fora da cidade.',
              respondedAt: '2026-07-30T18:21:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Não participarão')).toBe('1');
    });

    it('should count the same person only once when assigned to two functions', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
          commentators: [
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('4');
      expect(statByLabel('Confirmados')).toBe('1');
    });

    it('should keep the same person visible in both functional sections', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
          commentators: [
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Marcos Pereira');
      expect(sectionByTitle('Comentaristas').textContent).toContain('Marcos Pereira');
    });

    it('should dedupe a person present in three sections, using person.id as the key', async () => {
      const samePerson = (
        overrides: Partial<EventScheduleParticipationPersonSummary> = {},
      ): EventScheduleParticipationPersonSummary => ({
        id: 20,
        name: 'Marcos Pereira',
        participationStatus: 'CONFIRMED',
        declineReason: null,
        respondedAt: '2026-07-30T18:20:00',
        ...overrides,
      });

      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          priest: null,
          readers: [samePerson()],
          commentators: [samePerson()],
          ministersOfTheWord: [],
          eucharisticMinisters: [samePerson()],
        }),
      );

      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Marcos Pereira');
      expect(sectionByTitle('Comentaristas').textContent).toContain('Marcos Pereira');
      expect(sectionByTitle('Ministros da Eucaristia').textContent).toContain('Marcos Pereira');
      expect(statByLabel('Total de participantes')).toBe('1');
      expect(statByLabel('Confirmados')).toBe('1');
    });

    it('should count different people with the same name separately', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          priest: null,
          readers: [
            {
              id: 30,
              name: 'Joao Souza',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
            {
              id: 31,
              name: 'Joao Souza',
              participationStatus: 'PENDING',
              declineReason: null,
              respondedAt: null,
            },
          ],
          commentators: [],
          ministersOfTheWord: [],
          eucharisticMinisters: [],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('2');
      expect(statByLabel('Confirmados')).toBe('1');
      expect(statByLabel('Aguardando resposta')).toBe('1');
    });

    it('should count a participant listed twice in the same function only once', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          priest: null,
          readers: [
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
            {
              id: 20,
              name: 'Marcos Pereira',
              participationStatus: 'CONFIRMED',
              declineReason: null,
              respondedAt: '2026-07-30T18:20:00',
            },
          ],
          commentators: [],
          ministersOfTheWord: [],
          eucharisticMinisters: [],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('1');
      expect(statByLabel('Confirmados')).toBe('1');
    });

    it('should count the priest in the summary', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          readers: [],
          commentators: [],
          ministersOfTheWord: [],
          eucharisticMinisters: [],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('1');
    });

    it('should not count a null priest', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          priest: null,
          readers: [],
          commentators: [],
          ministersOfTheWord: [],
          eucharisticMinisters: [],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('0');
    });

    it('should handle empty participant lists without errors', async () => {
      await setup(
        '1',
        createDetail(),
        {},
        true,
        'escalas/eventos/:id',
        createParticipationDetail({
          priest: null,
          readers: [],
          commentators: [],
          ministersOfTheWord: [],
          eucharisticMinisters: [],
        }),
      );

      fixture.detectChanges();

      expect(statByLabel('Total de participantes')).toBe('0');
      expect(statByLabel('Confirmados')).toBe('0');
      expect(statByLabel('Aguardando resposta')).toBe('0');
      expect(statByLabel('Não participarão')).toBe('0');
    });

    it('should only show the refresh button for ROLE_ADMIN', async () => {
      await setup('1', createDetail(), {}, false);

      fixture.detectChanges();

      expect(textContent()).not.toContain('Atualizar participações');
    });

    it('should call the administrative endpoint once when refreshing', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.calls.reset();

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();

      expect(eventScheduleService.findParticipationByEventId).toHaveBeenCalledOnceWith(1);
    });

    it('should block a duplicate refresh click while pending', async () => {
      const pending = new Subject<EventScheduleParticipationDetailResponse>();
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(pending);
      eventScheduleService.findParticipationByEventId.calls.reset();

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      button.click();

      expect(eventScheduleService.findParticipationByEventId).toHaveBeenCalledTimes(1);

      pending.next(createParticipationDetail());
      pending.complete();
    });

    it('should keep the schedule visible while refreshing', async () => {
      const pending = new Subject<EventScheduleParticipationDetailResponse>();
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(pending);

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(textContent()).toContain('Missa de Domingo da manha');
      expect(textContent()).toContain('Atualizando participações...');

      pending.next(createParticipationDetail());
      pending.complete();
    });

    it('should disable the refresh button while refreshing', async () => {
      const pending = new Subject<EventScheduleParticipationDetailResponse>();
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(pending);

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(button.disabled).toBeTrue();

      pending.next(createParticipationDetail());
      pending.complete();
    });

    it('should not navigate while refreshing', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      const navigateSpy = spyOn(TestBed.inject(Router), 'navigate');

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not call the normal endpoint while refreshing', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    });

    it('should allow a new attempt after a refresh error', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(button.disabled).toBeFalse();

      eventScheduleService.findParticipationByEventId.and.returnValue(of(createParticipationDetail()));
      eventScheduleService.findParticipationByEventId.calls.reset();
      button.click();
      fixture.detectChanges();

      expect(eventScheduleService.findParticipationByEventId).toHaveBeenCalledOnceWith(1);
    });

    it('should show the refresh error with role="alert"', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      const feedback = participationSection()?.querySelector('.participation-summary__feedback');
      expect(feedback?.getAttribute('role')).toBe('alert');
    });

    it('should replace the statuses after a successful refresh', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(
        of(
          createParticipationDetail({
            readers: [
              {
                id: 4,
                name: 'Alice Lima',
                participationStatus: 'CONFIRMED',
                declineReason: null,
                respondedAt: '2026-07-30T18:20:00',
              },
              {
                id: 5,
                name: 'Arthur Costa',
                participationStatus: 'PENDING',
                declineReason: null,
                respondedAt: null,
              },
            ],
          }),
        ),
      );

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(sectionByTitle('Leitores').textContent).toContain('Participação confirmada');
    });

    it('should preserve the previously loaded data when the refresh fails', async () => {
      await setup('1', createDetail(), {}, true);
      fixture.detectChanges();
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      const button = participationSection()?.querySelector('button') as HTMLButtonElement;
      button.click();
      fixture.detectChanges();

      expect(textContent()).toContain('Missa de Domingo da manha');
      expect(textContent()).toContain('Alice Lima');
    });

    it('should use the administrative endpoint on retry for ROLE_ADMIN', async () => {
      await setup('1', createDetail(), {}, true);
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      fixture.detectChanges();
      fixture.detectChanges();

      eventScheduleService.findParticipationByEventId.and.returnValue(of(createParticipationDetail()));
      const retryButton = fixture.nativeElement.querySelector(
        '.event-schedule-detail__button',
      ) as HTMLButtonElement;
      retryButton.click();
      fixture.detectChanges();

      expect(eventScheduleService.findParticipationByEventId).toHaveBeenCalledTimes(2);
      expect(eventScheduleService.findByEventId).not.toHaveBeenCalled();
    });

    it('should use the normal endpoint on retry for ROLE_OPERATOR', async () => {
      await setup('1', createDetail(), {}, false);
      eventScheduleService.findByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );
      fixture.detectChanges();
      fixture.detectChanges();

      eventScheduleService.findByEventId.and.returnValue(of(createDetail()));
      const retryButton = fixture.nativeElement.querySelector(
        '.event-schedule-detail__button',
      ) as HTMLButtonElement;
      retryButton.click();
      fixture.detectChanges();

      expect(eventScheduleService.findByEventId).toHaveBeenCalledTimes(2);
      expect(eventScheduleService.findParticipationByEventId).not.toHaveBeenCalled();
    });

    it('should show a permission message for a 403 from the administrative endpoint', async () => {
      await setup('1', createDetail(), {}, true);
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      );

      fixture.detectChanges();
      fixture.detectChanges();

      expect(textContent()).toContain(
        'Você não possui permissão para consultar as participações desta escala.',
      );
    });

    it('should show a not-found message for a 404 from the administrative endpoint', async () => {
      await setup('1', createDetail(), {}, true);
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 404 })),
      );

      fixture.detectChanges();
      fixture.detectChanges();

      expect(textContent()).toContain('A escala do evento solicitado não foi encontrada.');
    });

    it('should show a generic message for other administrative errors', async () => {
      await setup('1', createDetail(), {}, true);
      eventScheduleService.findParticipationByEventId.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 500 })),
      );

      fixture.detectChanges();
      fixture.detectChanges();

      expect(textContent()).toContain(
        'Não foi possível carregar as participações da escala. Tente novamente.',
      );
    });

    it('should expose basic accessibility structure for the participation section', async () => {
      await setup('1', createDetail(), {}, true);

      fixture.detectChanges();

      const section = participationSection();
      const button = section?.querySelector('button') as HTMLButtonElement;

      expect(section?.querySelector('dl.participation-summary__stats')).not.toBeNull();
      expect(button.getAttribute('type')).toBe('button');
    });
  });

  describe('event detail context', () => {
    it('should use the event context wording and destination when accessed from the event detail route', async () => {
      const harness = await createEventDetailContextHarness('/eventos/1/escala');
      const backLink = harness.routeNativeElement?.querySelector('.page-action') as HTMLAnchorElement;

      expect(backLink.textContent).toContain('Voltar para o evento');
      expect(backLink.getAttribute('href')).toBe('/app/eventos/1');
    });

    it('should preserve all query parameters, including unknown ones, in the event context return link', async () => {
      const harness = await createEventDetailContextHarness(
        '/eventos/1/escala?period=past&search=domingo&type=mass&foo=bar',
      );
      const backLink = harness.routeNativeElement?.querySelector('.page-action') as HTMLAnchorElement;
      const href = backLink.getAttribute('href') ?? '';

      expect(href).toContain('/app/eventos/1');
      expect(href).toContain('period=past');
      expect(href).toContain('search=domingo');
      expect(href).toContain('type=mass');
      expect(href).toContain('foo=bar');
    });

    it('should show the edit action for administrators in the event detail context', async () => {
      const harness = await createEventDetailContextHarness('/eventos/1/escala', true);
      const links = Array.from(
        harness.routeNativeElement?.querySelectorAll('.page-action') ?? [],
      ) as HTMLAnchorElement[];
      const editLink = links.find((link) => link.textContent?.includes('Editar escala'));

      expect(editLink).toBeDefined();
      expect(editLink?.getAttribute('href')).toContain('/app/admin/escalas/eventos/1/editar');
    });

    it('should hide the edit action for operators in the event detail context', async () => {
      const harness = await createEventDetailContextHarness('/eventos/1/escala', false);

      expect(harness.routeNativeElement?.textContent).not.toContain('Editar escala');
    });

    async function createEventDetailContextHarness(
      url: string,
      isAdmin = false,
    ): Promise<RouterTestingHarness> {
      const authSessionServiceStub = jasmine.createSpyObj<AuthSessionService>('AuthSessionService', [
        'hasAuthority',
      ]);
      authSessionServiceStub.hasAuthority.and.returnValue(isAdmin);
      const eventScheduleServiceStub = jasmine.createSpyObj<EventScheduleService>(
        'EventScheduleService',
        ['findByEventId', 'findParticipationByEventId'],
      );
      eventScheduleServiceStub.findByEventId.and.returnValue(of(createDetail()));
      eventScheduleServiceStub.findParticipationByEventId.and.returnValue(
        of(toParticipationDetail(createDetail())),
      );

      await TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'eventos/:id/escala', component: EventScheduleDetailComponent }]),
          { provide: AuthSessionService, useValue: authSessionServiceStub },
          { provide: EventScheduleService, useValue: eventScheduleServiceStub },
        ],
      }).compileComponents();

      return RouterTestingHarness.create(url);
    }
  });

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function sectionByTitle(title: string): HTMLElement {
    const sections = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.schedule-section'),
    ) as HTMLElement[];
    const section = sections.find((element) => element.querySelector('h2')?.textContent?.trim() === title);

    expect(section).toBeDefined();

    return section as HTMLElement;
  }

  function namesIn(section: HTMLElement): (string | undefined)[] {
    return Array.from(section.querySelectorAll('li')).map((item) => item.textContent?.trim());
  }
});

function createDetail(
  overrides: Partial<EventScheduleDetailResponse> = {},
): EventScheduleDetailResponse {
  return {
    eventId: 1,
    eventName: 'Missa de Domingo da manha',
    eventDate: '2025-07-13',
    eventTime: '10:00:00',
    massOrCelebration: true,
    location: {
      id: 1,
      churchName: 'Igreja Matriz Nossa Senhora do Rosario',
    },
    priest: {
      id: 13,
      name: 'Padre Miguel',
    },
    readers: [
      { id: 4, name: 'Alice Lima' },
      { id: 5, name: 'Arthur Costa' },
    ],
    commentators: [{ id: 1, name: 'Luana Odinson' }],
    ministersOfTheWord: [{ id: 7, name: 'Davi Gomes' }],
    eucharisticMinisters: [{ id: 10, name: 'Mariana Ferraz' }],
    ...overrides,
  };
}

function toParticipationPerson(
  person: EventSchedulePersonSummary,
): EventScheduleParticipationPersonSummary {
  return {
    ...person,
    participationStatus: 'PENDING',
    declineReason: null,
    respondedAt: null,
  };
}

function toParticipationDetail(
  detail: EventScheduleDetailResponse,
): EventScheduleParticipationDetailResponse {
  return {
    eventId: detail.eventId,
    eventName: detail.eventName,
    eventDate: detail.eventDate,
    eventTime: detail.eventTime,
    massOrCelebration: detail.massOrCelebration,
    location: detail.location,
    priest: detail.priest ? toParticipationPerson(detail.priest) : null,
    readers: detail.readers.map(toParticipationPerson),
    commentators: detail.commentators.map(toParticipationPerson),
    ministersOfTheWord: detail.ministersOfTheWord.map(toParticipationPerson),
    eucharisticMinisters: detail.eucharisticMinisters.map(toParticipationPerson),
  };
}

function createParticipationDetail(
  overrides: Partial<EventScheduleParticipationDetailResponse> = {},
): EventScheduleParticipationDetailResponse {
  return {
    ...toParticipationDetail(createDetail()),
    ...overrides,
  };
}
