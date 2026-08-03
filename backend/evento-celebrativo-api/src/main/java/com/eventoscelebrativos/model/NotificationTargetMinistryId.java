package com.eventoscelebrativos.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class NotificationTargetMinistryId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long notification;
    private MinistryType ministryType;

    public NotificationTargetMinistryId() {
    }

    public NotificationTargetMinistryId(Long notification, MinistryType ministryType) {
        this.notification = notification;
        this.ministryType = ministryType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationTargetMinistryId that = (NotificationTargetMinistryId) o;
        return Objects.equals(notification, that.notification) && ministryType == that.ministryType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(notification, ministryType);
    }
}
