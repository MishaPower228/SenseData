package com.example.sensedata.model;

// Проста DTO-модель для PUT /api/displaydata/ownership
public class SensorOwnershipUpdateDto {
    public String chipId;
    public String roomName;   // може бути null — тоді не оновлюємо
    public String imageName;  // може бути null — тоді не оновлюємо

    public SensorOwnershipUpdateDto(String chipId, String roomName, String imageName) {
        this.chipId = chipId;
        this.roomName = roomName;
        this.imageName = imageName;
    }
}
