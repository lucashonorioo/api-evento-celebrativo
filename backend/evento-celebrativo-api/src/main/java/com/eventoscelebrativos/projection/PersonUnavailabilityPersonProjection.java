package com.eventoscelebrativos.projection;

import java.time.LocalDate;

public interface PersonUnavailabilityPersonProjection {

    Long getPersonId();
    String getPersonName();
    LocalDate getStartDate();
    LocalDate getEndDate();
}
