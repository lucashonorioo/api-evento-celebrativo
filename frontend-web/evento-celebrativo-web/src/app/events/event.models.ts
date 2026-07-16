export interface CelebrationEventResponse {
  id: number;
  nameMassOrEvent: string;
  eventDate: string;
  eventTime: string;
  massOrCelebration: boolean;
}

export interface CelebrationEventRequest {
  nameMassOrEvent: string;
  eventDate: string;
  eventTime: string;
  massOrCelebration: boolean;
}
