package com.example.sensedata.model;

public class SensorOwnershipRequestDTO {
    private String roomName;
    private String imageName;
    private String chipId;
    private int userId;

    public SensorOwnershipRequestDTO(String roomName, String imageName, String chipId, int userId) {
        this.roomName = roomName;
        this.imageName = imageName;
        this.chipId = chipId;
        this.userId = userId;
    }

    // Геттери
    public int getUserId() { return userId; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }

    // Сеттери
    public void setUserId(int userId) { this.userId = userId; }
    public void setChipId(String chipId) { this.chipId = chipId; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
}
