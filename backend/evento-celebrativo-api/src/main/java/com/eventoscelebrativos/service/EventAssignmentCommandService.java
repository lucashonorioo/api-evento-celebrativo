package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;
import com.eventoscelebrativos.model.EventAssignment;

import java.util.Collection;

public interface EventAssignmentCommandService {

    void synchronizeAssignments(
            CelebrationEvent event,
            Collection<EventAssignment> currentAssignments,
            Collection<EventAssignmentTarget> targets
    );

    void deleteAllForEvent(Long eventId);
}
