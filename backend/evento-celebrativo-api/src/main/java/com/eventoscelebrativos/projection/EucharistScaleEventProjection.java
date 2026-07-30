package com.eventoscelebrativos.projection;

import java.time.LocalDateTime;

public interface EucharistScaleEventProjection {

    Long getEventId();
    String getNameMassOrEvent();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    String getChurchName();
    String getMinisterNames();

}
