package com.eventoscelebrativos.dto.response;

public class UnreadNotificationCountResponseDTO {

    private Long unreadCount;

    public UnreadNotificationCountResponseDTO() {
    }

    public UnreadNotificationCountResponseDTO(Long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public Long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
