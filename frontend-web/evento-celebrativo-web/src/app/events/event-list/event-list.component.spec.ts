import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter, RouterOutlet } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';

import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';
import { EventListComponent } from './event-list.component';

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

describe('EventListComponent', () => {
  let fixture: ComponentFixture<EventListComponent>;
  let component: EventListComponent;
  let eventService: jasmine.SpyObj<EventService>;

  const events: CelebrationEventResponse[] = [
    {
      id: 1,
      nameMassOrEvent: 'Missa de Domingo',
      eventDate: '2026-08-02',
      eventTime: '10:30:00',
      massOrCelebration: true,
    },
    {
      id: 2,
      nameMassOrEvent: 'Celebração da Palavra',
      eventDate: '2026-08-08',
      eventTime: '19:45:00',
      massOrCelebration: false,
    },
  ];

  async function setup(response = of(events)): Promise<void> {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(response);

    await TestBed.configureTestingModule({
      imports: [EventListComponent],
      providers: [provideRouter([]), { provide: EventService, useValue: eventService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EventListComponent);
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

  it('should load events when initialized', async () => {
    await setup();

    fixture.detectChanges();

    expect(eventService.findAll).toHaveBeenCalledOnceWith();
  });

  it('should render the loading state while events are being requested', async () => {
    const pendingEvents = new Subject<CelebrationEventResponse[]>();
    await setup(pendingEvents.asObservable());

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(component.isLoading()).toBeTrue();
    expect(compiled.textContent).toContain('Carregando eventos...');

    pendingEvents.next(events);
    pendingEvents.complete();
  });

  it('should render returned events preserving the API order', async () => {
    await setup();

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = Array.from(compiled.querySelectorAll('.event-card'));

    expect(cards.length).toBe(2);
    expect(cards[0].textContent).toContain('Missa de Domingo');
    expect(cards[1].textContent).toContain('Celebração da Palavra');
  });

  it('should render the event type labels', async () => {
    await setup();

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Missa');
    expect(compiled.textContent).toContain('Celebração');
  });

  it('should format event dates and times for display', async () => {
    await setup();

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('02/08/2026');
    expect(compiled.textContent).toContain('10:30');
    expect(compiled.textContent).toContain('08/08/2026');
    expect(compiled.textContent).toContain('19:45');
  });

  it('should render an empty state when there are no events', async () => {
    await setup(of([]));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Nenhum evento encontrado.');
  });

  it('should render an error state when the request fails', async () => {
    await setup(throwError(() => new Error('Request failed')));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(component.errorMessage()).toBe('Não foi possível carregar os eventos. Tente novamente.');
    expect(compiled.textContent).toContain('Não foi possível carregar os eventos.');
    expect(compiled.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('should retry loading events when the retry button is clicked', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValues(
      throwError(() => new Error('Request failed')),
      of(events),
    );

    await TestBed.configureTestingModule({
      imports: [EventListComponent],
      providers: [provideRouter([]), { provide: EventService, useValue: eventService }],
    }).compileComponents();

    fixture = TestBed.createComponent(EventListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.events__button') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(eventService.findAll).toHaveBeenCalledTimes(2);
    expect(compiled.textContent).toContain('Missa de Domingo');
  });

  it('should render public detail links relative to the public list route', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(events));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'eventos', component: EventListComponent },
          { path: 'eventos/:id', component: EmptyTestComponent },
        ]),
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/eventos');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/eventos/1');
    expect(link?.textContent).toContain('Ver detalhes');
  });

  it('should render authenticated detail links relative to the authenticated list route', async () => {
    eventService = jasmine.createSpyObj<EventService>('EventService', ['findAll']);
    eventService.findAll.and.returnValue(of(events));

    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: 'app',
            component: TestShellComponent,
            children: [
              { path: 'eventos', component: EventListComponent },
              { path: 'eventos/:id', component: EmptyTestComponent },
            ],
          },
        ]),
        { provide: EventService, useValue: eventService },
      ],
    }).compileComponents();

    const harness = await RouterTestingHarness.create('/app/eventos');
    const link = harness.routeNativeElement?.querySelector(
      '.event-card__link',
    ) as HTMLAnchorElement | null;

    expect(link?.getAttribute('href')).toBe('/app/eventos/1');
  });
});
