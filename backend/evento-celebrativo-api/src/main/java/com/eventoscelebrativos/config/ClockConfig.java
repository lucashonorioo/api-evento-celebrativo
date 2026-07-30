package com.eventoscelebrativos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
