package com.eventoscelebrativos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.event-assignment.shadow-read")
public class EventAssignmentShadowReadProperties {

    private boolean eventDetailEnabled = false;
    private boolean eventScaleDetailEnabled = false;
    private boolean monthlyScheduleEnabled = false;
    private boolean eucharistScaleEnabled = false;

    public boolean isEventDetailEnabled() {
        return eventDetailEnabled;
    }

    public void setEventDetailEnabled(boolean eventDetailEnabled) {
        this.eventDetailEnabled = eventDetailEnabled;
    }

    public boolean isEventScaleDetailEnabled() {
        return eventScaleDetailEnabled;
    }

    public void setEventScaleDetailEnabled(boolean eventScaleDetailEnabled) {
        this.eventScaleDetailEnabled = eventScaleDetailEnabled;
    }

    public boolean isMonthlyScheduleEnabled() {
        return monthlyScheduleEnabled;
    }

    public void setMonthlyScheduleEnabled(boolean monthlyScheduleEnabled) {
        this.monthlyScheduleEnabled = monthlyScheduleEnabled;
    }

    public boolean isEucharistScaleEnabled() {
        return eucharistScaleEnabled;
    }

    public void setEucharistScaleEnabled(boolean eucharistScaleEnabled) {
        this.eucharistScaleEnabled = eucharistScaleEnabled;
    }
}
