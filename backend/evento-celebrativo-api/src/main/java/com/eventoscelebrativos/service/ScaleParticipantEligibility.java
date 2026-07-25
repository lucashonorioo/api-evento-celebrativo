package com.eventoscelebrativos.service;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.Person;

public record ScaleParticipantEligibility(
        Long personId,
        MinistryType ministryType,
        boolean personFound,
        boolean ministryAssigned,
        Person person
) {
    public boolean isEligible() {
        return personFound && ministryAssigned;
    }
}
