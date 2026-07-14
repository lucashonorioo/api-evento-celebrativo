export interface EucharistScheduleQuery {
  startDate: string;
  endDate: string;
  page: number;
  size: number;
}

export interface EucharistScheduleResponse {
  nameMassOrEvent: string;
  eventDate: string;
  eventTime: string;
  churchName: string;
  nameMinisters: string[];
}

export interface EucharistSchedulePage {
  content: EucharistScheduleResponse[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
