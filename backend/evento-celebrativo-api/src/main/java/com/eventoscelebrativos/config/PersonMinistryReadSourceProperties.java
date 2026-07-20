package com.eventoscelebrativos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.person-ministry.read-source")
public class PersonMinistryReadSourceProperties {

    private PersonMinistryReadSource reader = PersonMinistryReadSource.LEGACY;
    private PersonMinistryReadSource commentator = PersonMinistryReadSource.LEGACY;

    public PersonMinistryReadSource getReader() {
        return reader;
    }

    public void setReader(PersonMinistryReadSource reader) {
        this.reader = reader;
    }

    public PersonMinistryReadSource getCommentator() {
        return commentator;
    }

    public void setCommentator(PersonMinistryReadSource commentator) {
        this.commentator = commentator;
    }
}
