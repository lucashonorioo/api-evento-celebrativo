package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface PersonUnavailabilityPersonProjection {

    Long getPersonId();
    String getPersonName();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
}
