package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface EventScheduleEventProjection {

    Long getEventId();
    String getEventName();
    LocalDate getEventDate();
    LocalTime getEventTime();
    Boolean getMassOrCelebration();
    Long getLocationId();
    String getChurchName();
}
