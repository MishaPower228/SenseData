package com.example.sensedata.model.room;

import com.google.gson.annotations.SerializedName;

public class RoomWithSensorDto {
    @SerializedName("id")        private int id;
    @SerializedName("chipId")    private String chipId;
    @SerializedName("roomName")  private String roomName;   // сервер може віддати "RoomName"
    @SerializedName("imageName") private final String imageName;
    @SerializedName("temperature") private final Double temperature; // може бути null
    @SerializedName("humidity")    private final Double humidity;   // може бути null

    public RoomWithSensorDto(String imageName, Double temperature, Double humidity) {
        this.imageName = imageName;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public int getId() { return id; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }
    public Double getTemperature() { return temperature; }
    public Double getHumidity() { return humidity; }
    public void setId(int id) { this.id = id; }
    public void setChipId(String chipId) { this.chipId = chipId; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}
