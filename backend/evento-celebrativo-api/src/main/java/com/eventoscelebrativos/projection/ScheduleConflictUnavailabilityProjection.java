package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface ScheduleConflictUnavailabilityProjection {

    Long getPersonId();
    Long getId();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
}
