package com.eventoscelebrativos.dto.response;

public class CelebrationEventScaleLocationResponseDTO {

    private Long id;
    private String churchName;

    public CelebrationEventScaleLocationResponseDTO() {
    }

    public CelebrationEventScaleLocationResponseDTO(Long id, String churchName) {
        this.id = id;
        this.churchName = churchName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }
}
