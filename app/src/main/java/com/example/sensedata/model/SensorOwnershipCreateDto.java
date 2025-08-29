package com.example.sensedata.model;

public class SensorOwnershipCreateDto {
    private String chipId;
    private int userId;
    private String roomName;
    private String imageName;

    public SensorOwnershipCreateDto(String chipId, int userId, String roomName, String imageName) {
        this.chipId = chipId;
        this.userId = userId;
        this.roomName = roomName;
        this.imageName = imageName;
    }

    public int getUserId() { return userId; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }
}
