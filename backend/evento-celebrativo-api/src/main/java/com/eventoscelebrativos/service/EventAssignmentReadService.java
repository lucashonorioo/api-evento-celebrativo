package com.eventoscelebrativos.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface EventAssignmentReadService {

    List<EventAssignmentSnapshot> findAllByEventId(Long eventId);

    Map<Long, List<EventAssignmentSnapshot>> findAllByEventIds(Collection<Long> eventIds);
}
