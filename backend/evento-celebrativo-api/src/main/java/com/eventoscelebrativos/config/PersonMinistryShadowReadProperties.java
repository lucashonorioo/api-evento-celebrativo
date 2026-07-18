package com.eventoscelebrativos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.person-ministry.shadow-read")
public class PersonMinistryShadowReadProperties {

    private boolean readerEnabled = false;

    public boolean isReaderEnabled() {
        return readerEnabled;
    }

    public void setReaderEnabled(boolean readerEnabled) {
        this.readerEnabled = readerEnabled;
    }
}
