package com.eventoscelebrativos.model;

public enum EventScheduleType {

    PRIEST("priest"),
    READER("reader"),
    COMMENTATOR("commentator"),
    MINISTER_OF_THE_WORD("minister_of_the_word"),
    EUCHARISTIC_MINISTER("eucharistic_minister");

    private final String personType;

    EventScheduleType(String personType) {
        this.personType = personType;
    }

    public String getPersonType() {
        return personType;
    }
}
