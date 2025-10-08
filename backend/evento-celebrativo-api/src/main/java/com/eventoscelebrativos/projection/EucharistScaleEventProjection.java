package com.eventoscelebrativos.projection;

import java.time.LocalDate;
import java.time.LocalTime;

public interface EucharistScaleEventProjection {

    String getNameMassOrEvent();
    LocalDate getEventDate();
    LocalTime getEventTime();
    String getChurchName();
    String getMinisterName();

}
