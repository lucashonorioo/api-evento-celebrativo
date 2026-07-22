package com.eventoscelebrativos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.event-assignment.read-source")
public class EventAssignmentReadSourceProperties {

    private EventAssignmentReadSource eventScaleDetail = EventAssignmentReadSource.LEGACY;

    public EventAssignmentReadSource getEventScaleDetail() {
        return eventScaleDetail;
    }

    public void setEventScaleDetail(EventAssignmentReadSource eventScaleDetail) {
        this.eventScaleDetail = eventScaleDetail;
    }
}
