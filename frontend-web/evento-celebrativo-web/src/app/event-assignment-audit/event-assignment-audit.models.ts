export type EventAssignmentAuditIssueType =
  | 'MISSING_PARALLEL_ASSIGNMENT'
  | 'EXTRA_PARALLEL_ASSIGNMENT'
  | 'ASSIGNMENT_TYPE_MISMATCH'
  | 'DUPLICATE_PARALLEL_ASSIGNMENT'
  | 'MULTIPLE_PRIESTS'
  | 'UNKNOWN_LEGACY_PERSON_TYPE';

export type EventAssignmentType =
  | 'PRIEST'
  | 'READER'
  | 'COMMENTATOR'
  | 'MINISTER_OF_THE_WORD'
  | 'EUCHARISTIC_MINISTER';

export interface EventAssignmentAuditQuery {
  readonly startDate?: string;
  readonly endDate?: string;
  readonly eventId?: number;
  readonly page?: number;
  readonly size?: number;
  readonly includeDetails?: boolean;
}

export interface EventAssignmentAuditSummary {
  readonly eventsChecked: number;
  readonly consistentEvents: number;
  readonly inconsistentEvents: number;
  readonly legacyParticipants: number;
  readonly parallelAssignments: number;
  readonly totalIssues: number;
  readonly missingParallelAssignments: number;
  readonly extraParallelAssignments: number;
  readonly assignmentTypeMismatches: number;
  readonly duplicateParallelAssignments: number;
  readonly multiplePriests: number;
  readonly unknownLegacyPersonTypes: number;
}

export interface EventAssignmentAuditIssue {
  readonly issueType: EventAssignmentAuditIssueType;
  readonly eventId: number | null;
  readonly personId: number | null;
  readonly legacyType: EventAssignmentType | null;
  readonly parallelType: EventAssignmentType | null;
}

export interface EventAssignmentAuditEvent {
  readonly eventId: number;
  readonly consistent: boolean;
  readonly legacyParticipantCount: number;
  readonly parallelAssignmentCount: number;
  readonly issueCount: number;
  readonly issues: readonly EventAssignmentAuditIssue[];
}

export interface EventAssignmentAuditResponse {
  readonly summary: EventAssignmentAuditSummary;
  readonly page: number;
  readonly size: number;
  readonly totalElements: number;
  readonly totalPages: number;
  readonly numberOfElements: number;
  readonly empty: boolean;
  readonly events?: readonly EventAssignmentAuditEvent[];
}
