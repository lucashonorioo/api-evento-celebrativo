package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.EventAssignmentType;
import com.eventoscelebrativos.model.Person;

public record EventAssignmentTarget(Person person, EventAssignmentType assignmentType) {
}
