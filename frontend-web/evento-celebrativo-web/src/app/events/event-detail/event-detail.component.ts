import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, EMPTY, finalize, Observable, switchMap } from 'rxjs';

import { CelebrationEventResponse } from '../event.models';
import { EventService } from '../event.service';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly eventService = inject(EventService);
  private readonly destroyRef = inject(DestroyRef);

  readonly event = signal<CelebrationEventResponse | null>(null);
  readonly isLoading = signal(false);
  readonly notFound = signal(false);
  readonly errorMessage = signal<string | null>(null);

  private lastValidEventId: number | null = null;

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap((paramMap) => {
          const eventId = this.parseEventId(paramMap.get('id'));

          if (eventId === null) {
            this.setNotFoundState();
            return EMPTY;
          }

          return this.loadEvent(eventId);
        }),
      )
      .subscribe((event) => {
        this.event.set(event);
      });
  }

  retry(): void {
    if (this.lastValidEventId === null) {
      return;
    }

    this.loadEvent(this.lastValidEventId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        this.event.set(event);
      });
  }

  getEventType(event: CelebrationEventResponse): string {
    return event.massOrCelebration ? 'Missa' : 'Celebração';
  }

  formatTime(eventTime: string): string {
    return eventTime.slice(0, 5);
  }

  private loadEvent(eventId: number): Observable<CelebrationEventResponse> {
    this.lastValidEventId = eventId;
    this.event.set(null);
    this.isLoading.set(true);
    this.notFound.set(false);
    this.errorMessage.set(null);

    return this.eventService.findById(eventId).pipe(
      catchError((error: unknown) => {
        this.event.set(null);

        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notFound.set(true);
        } else {
          this.errorMessage.set('Não foi possível carregar o evento. Tente novamente.');
        }

        return EMPTY;
      }),
      finalize(() => {
        this.isLoading.set(false);
      }),
    );
  }

  private setNotFoundState(): void {
    this.event.set(null);
    this.isLoading.set(false);
    this.notFound.set(true);
    this.errorMessage.set(null);
    this.lastValidEventId = null;
  }

  private parseEventId(value: string | null): number | null {
    if (value === null || !/^\d+$/.test(value)) {
      return null;
    }

    const eventId = Number(value);

    if (!Number.isSafeInteger(eventId) || eventId <= 0) {
      return null;
    }

    return eventId;
  }
}
