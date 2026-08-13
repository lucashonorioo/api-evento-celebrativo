package com.eventoscelebrativos.dto.response;

import com.eventoscelebrativos.model.ParishResponsibilityType;

import java.time.LocalDateTime;

public class ParishStaffAssignmentDTO {

    private ParishResponsibilityType responsibility;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ParishStaffAssignmentDTO() {
    }

    public ParishStaffAssignmentDTO(
            ParishResponsibilityType responsibility,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.responsibility = responsibility;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public ParishResponsibilityType getResponsibility() {
        return responsibility;
    }

    public void setResponsibility(ParishResponsibilityType responsibility) {
        this.responsibility = responsibility;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
