package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface ScheduleUnavailabilityConflictKeyProjection {

    Long getEventId();
    String getEventName();
    LocalDateTime getEventStartAt();
    LocalDateTime getEventEndAt();
    Long getPersonId();
    String getPersonName();
    String getAssignmentType();
}
