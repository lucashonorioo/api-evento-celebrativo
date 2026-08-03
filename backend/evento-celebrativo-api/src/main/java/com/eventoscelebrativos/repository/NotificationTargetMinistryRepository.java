package com.eventoscelebrativos.repository;

import com.eventoscelebrativos.model.MinistryType;
import com.eventoscelebrativos.model.NotificationTargetMinistry;
import com.eventoscelebrativos.model.NotificationTargetMinistryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationTargetMinistryRepository
        extends JpaRepository<NotificationTargetMinistry, NotificationTargetMinistryId> {

    @Query("""
            SELECT m.ministryType FROM NotificationTargetMinistry m
            WHERE m.notification.id = :notificationId
            ORDER BY m.ministryType ASC
            """)
    List<MinistryType> findMinistryTypesByNotificationId(@Param("notificationId") Long notificationId);
}
