import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventListComponent implements OnInit {
  private readonly eventService = inject(EventService);

  readonly events = signal<CelebrationEventResponse[]>([]);
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.loadEvents();
  }

  loadEvents(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.eventService
      .findAll()
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (events) => {
          this.events.set(events);
        },
        error: () => {
          this.events.set([]);
          this.errorMessage.set('Não foi possível carregar os eventos. Tente novamente.');
        },
      });
  }

  getEventType(event: CelebrationEventResponse): string {
    return event.massOrCelebration ? 'Missa' : 'Celebração';
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }
}
