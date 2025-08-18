package com.example.sensedata.model;

public class SensorOwnershipCreateDto {
    private int userId;
    private String chipId;
    private String roomName;
    private String imageName;

    public SensorOwnershipCreateDto(int userId, String chipId, String roomName, String imageName) {
        this.userId = userId;
        this.chipId = chipId;
        this.roomName = roomName;
        this.imageName = imageName;
    }

    public int getUserId() { return userId; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }
}
