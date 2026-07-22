package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface EucharistScaleEventProjection {

    Long getEventId();
    String getNameMassOrEvent();
    LocalDate getEventDate();
    LocalTime getEventTime();
    String getChurchName();
    String getMinisterNames();

}
