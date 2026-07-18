package com.eventoscelebrativos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.person-ministry.shadow-read")
public class PersonMinistryShadowReadProperties {

    private boolean readerEnabled = false;
    private boolean commentatorEnabled = false;
    private boolean priestEnabled = false;
    private boolean ministerOfTheWordEnabled = false;
    private boolean eucharisticMinisterEnabled = false;

    public boolean isReaderEnabled() {
        return readerEnabled;
    }

    public void setReaderEnabled(boolean readerEnabled) {
        this.readerEnabled = readerEnabled;
    }

    public boolean isCommentatorEnabled() {
        return commentatorEnabled;
    }

    public void setCommentatorEnabled(boolean commentatorEnabled) {
        this.commentatorEnabled = commentatorEnabled;
    }

    public boolean isPriestEnabled() {
        return priestEnabled;
    }

    public void setPriestEnabled(boolean priestEnabled) {
        this.priestEnabled = priestEnabled;
    }

    public boolean isMinisterOfTheWordEnabled() {
        return ministerOfTheWordEnabled;
    }

    public void setMinisterOfTheWordEnabled(boolean ministerOfTheWordEnabled) {
        this.ministerOfTheWordEnabled = ministerOfTheWordEnabled;
    }

    public boolean isEucharisticMinisterEnabled() {
        return eucharisticMinisterEnabled;
    }

    public void setEucharisticMinisterEnabled(boolean eucharisticMinisterEnabled) {
        this.eucharisticMinisterEnabled = eucharisticMinisterEnabled;
    }
}
