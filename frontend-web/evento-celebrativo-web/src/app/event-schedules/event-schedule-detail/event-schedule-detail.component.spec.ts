import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { EventScheduleDetailResponse } from '../event-schedule.models';
import { EventScheduleService } from '../event-schedule.service';
import { EventScheduleDetailComponent } from './event-schedule-detail.component';

describe('EventScheduleDetailComponent', () => {
  let component: EventScheduleDetailComponent;
  let fixture: ComponentFixture<EventScheduleDetailComponent>;
  let eventScheduleService: jasmine.SpyObj<EventScheduleService>;

  async function setup(
    routeId: string | null = '1',
    response: EventScheduleDetailResponse = createDetail(),
    queryParams: Record<string, string> = {},
  ): Promise<void> {
    eventScheduleService = jasmine.createSpyObj<EventScheduleService>('EventScheduleService', [
      'findByEventId',
    ]);
    eventScheduleService.findByEventId.and.returnValue(of(response));

    await TestBed.configureTestingModule({
      imports: [EventScheduleDetailComponent],
      providers: [
        provideRouter([]),
        { provide: EventScheduleService, useValue: eventScheduleService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap(routeId === null ? {} : { id: routeId }),
              queryParamMap: convertToParamMap(queryParams),
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

  function textContent(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
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
