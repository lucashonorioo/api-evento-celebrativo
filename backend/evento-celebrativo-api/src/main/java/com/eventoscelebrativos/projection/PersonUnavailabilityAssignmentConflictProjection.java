package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface PersonUnavailabilityAssignmentConflictProjection {

    Long getEventId();
    String getEventName();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    String getAssignmentType();
}
