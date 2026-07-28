package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface PersonScheduleEventProjection {

    Long getEventId();
    String getEventName();
    LocalDate getEventDate();
    LocalTime getEventTime();
    Boolean getMassOrCelebration();
    Long getLocationId();
    String getLocationName();
}
