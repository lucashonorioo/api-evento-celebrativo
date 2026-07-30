package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface PersonUnavailabilityAssignmentConflictProjection {

    Long getEventId();
    String getEventName();
    LocalDate getEventDate();
    LocalTime getEventTime();
    String getAssignmentType();
}
