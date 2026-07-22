package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.CelebrationEvent;

import java.util.Collection;

public interface EventAssignmentCompatibilityService {

    void synchronizeAssignments(CelebrationEvent event, Collection<EventAssignmentTarget> targets);

    void deleteAllForEvent(Long eventId);
}
