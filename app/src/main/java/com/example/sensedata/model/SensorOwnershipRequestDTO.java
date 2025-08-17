package com.example.sensedata.model;

public class SensorOwnershipRequestDTO {
    private String roomName;
    private String imageName;
    private String chipId;
    private String username;

    public SensorOwnershipRequestDTO(String roomName, String imageName, String chipId, String username) {
        this.roomName = roomName;
        this.imageName = imageName;
        this.chipId = chipId;
        this.username = username;
    }

    // Геттери
    public String getUsername() { return username; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }

    // Сеттери
    public void setUsername(String username) { this.username = username; }
    public void setChipId(String chipId) { this.chipId = chipId; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
}
