import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
  RouterOutlet,
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';

import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import { EventDetailComponent } from './event-detail.component';

@Component({
  selector: 'app-test-shell',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class TestShellComponent {}

@Component({
  selector: 'app-empty-test',
  standalone: true,
  template: '',
})
class EmptyTestComponent {}

describe('EventDetailComponent', () => {
  let fixture: ComponentFixture<EventDetailComponent>;
  let component: EventDetailComponent;
  let eventService: jasmine.SpyObj<EventService>;
  let paramMapSubject: BehaviorSubject<ParamMap>;

  const massEvent: CelebrationEventResponse = {
    id: 1,
    nameMassOrEvent: 'Missa de Domingo',
    eventDate: '2026-08-02',
    eventTime: '10:30:00',
    massOrCelebration: true,
  };

  const celebrationEvent: CelebrationEventResponse = {
    id: 2,
    nameMassOrEvent: 'Celebração da Palavra',
    eventDate: '2026-08-08',
    eventTime: '19:45:00',
    massOrCelebration: false,
  };

  async function setup(
    id: string = '1',
    response = of(massEvent),
  ): Promise<void> {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findById']);
    eventService.findById.and.returnValue(response);
    paramMapSubject = new BehaviorSubject(convertToParamMap({ id }));

    await TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideRouter([]),
        { provide: EventService, useValue: eventService },
        { provide: ActivatedRoute, useValue: { paramMap: paramMapSubject.asObservable() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventDetailComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should create', async () => {
    await setup();

    fixture.detectChanges();

    expect(component).toBeTruthy();
  });

  it('should request the event when route id is valid', async () => {
    await setup('1');

    fixture.detectChanges();

    expect(eventService.findById).toHaveBeenCalledOnceWith(1);
  });

  it('should render the event name and type for a mass', async () => {
    await setup('1', of(massEvent));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Missa de Domingo');
    expect(compiled.textContent).toContain('Missa');
  });

  it('should render the event type for a celebration', async () => {
    await setup('2', of(celebrationEvent));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Celebração');
  });

  it('should format event date and time', async () => {
    await setup('1', of(massEvent));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('02/08/2026');
    expect(compiled.textContent).toContain('10:30');
  });

  it('should render loading state while event is being requested', async () => {
    const pendingEvent = new Subject<CelebrationEventResponse>();
    await setup('1', pendingEvent.asObservable());

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(component.isLoading()).toBeTrue();
    expect(compiled.textContent).toContain('Carregando evento...');

    pendingEvent.next(massEvent);
    pendingEvent.complete();
  });

  it('should not request the API when id is invalid', async () => {
    await setup('abc');

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(eventService.findById).not.toHaveBeenCalled();
    expect(component.notFound()).toBeTrue();
    expect(compiled.textContent).toContain('Evento não encontrado');
  });

  it('should not request the API when id is not a positive integer', async () => {
    await setup('0');

    fixture.detectChanges();

    expect(eventService.findById).not.toHaveBeenCalled();

    paramMapSubject.next(convertToParamMap({ id: '-1' }));
    fixture.detectChanges();

    expect(eventService.findById).not.toHaveBeenCalled();
  });

  it('should render not found state after a 404 error', async () => {
    await setup(
      '99',
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(component.notFound()).toBeTrue();
    expect(compiled.textContent).toContain('Evento não encontrado');
    expect(compiled.querySelector('.event-detail__button')).toBeNull();
  });

  it('should render a recoverable error state after a generic error', async () => {
    await setup(
      '1',
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
    );

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(component.errorMessage()).toBe('Não foi possível carregar o evento. Tente novamente.');
    expect(compiled.querySelector('[role="alert"]')).not.toBeNull();
    expect(compiled.querySelector('.event-detail__button')?.textContent).toContain(
      'Tentar novamente',
    );
  });

  it('should retry loading the last valid event id after a recoverable error', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findById']);
    eventService.findById.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500, statusText: 'Server Error' })),
      of(massEvent),
    );
    paramMapSubject = new BehaviorSubject(convertToParamMap({ id: '1' }));

    await TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideRouter([]),
        { provide: EventService, useValue: eventService },
        { provide: ActivatedRoute, useValue: { paramMap: paramMapSubject.asObservable() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EventDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector(
      '.event-detail__button',
    ) as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(eventService.findById).toHaveBeenCalledTimes(2);
    expect(compiled.textContent).toContain('Missa de Domingo');
  });

  it('should render a back link to the parent route', async () => {
    await setup();

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const link = compiled.querySelector('.event-detail__back') as HTMLAnchorElement | null;

    expect(link?.textContent).toContain('Voltar para eventos');
    expect(link?.getAttribute('href')).toBe('/');
  });

  it('should render public back link relative to the public detail route', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findById']);
    eventService.findById.and.returnValue(of(massEvent));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', component: EmptyTestComponent },
          { path: 'eventos/:id', component: EventDetailComponent },
        ]),
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/eventos/1');
    const link = harness.routeNativeElement?.querySelector(
      '.event-detail__back',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/eventos');
  });

  it('should render authenticated back link relative to the authenticated detail route', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findById']);
    eventService.findById.and.returnValue(of(massEvent));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'app',
            component: TestShellComponent,
            children: [
              { path: 'eventos', component: EmptyTestComponent },
              { path: 'eventos/:id', component: EventDetailComponent },
            ],
          },
        ]),
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/app/eventos/1');
    const link = harness.routeNativeElement?.querySelector(
      '.event-detail__back',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/app/eventos');
  });
});
