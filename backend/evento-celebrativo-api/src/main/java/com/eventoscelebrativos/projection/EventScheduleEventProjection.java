package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface EventScheduleEventProjection {

    Long getEventId();
    String getEventName();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    Boolean getMassOrCelebration();
    Long getLocationId();
    String getChurchName();
}
