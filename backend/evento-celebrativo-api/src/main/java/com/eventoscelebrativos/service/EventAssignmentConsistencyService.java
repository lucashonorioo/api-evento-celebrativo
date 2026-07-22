package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface EventAssignmentConsistencyService {

    EventAssignmentConsistencyReport compareEvent(
            CelebrationEvent legacyEvent,
            List<EventAssignmentSnapshot> parallelAssignments
    );

    Map<Long, EventAssignmentConsistencyReport> compareEvents(
            Collection<CelebrationEvent> legacyEvents,
            Map<Long, List<EventAssignmentSnapshot>> parallelAssignments
    );
}
