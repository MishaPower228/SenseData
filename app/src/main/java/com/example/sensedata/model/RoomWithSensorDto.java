package com.example.sensedata.model;

import com.google.gson.annotations.SerializedName;

public class RoomWithSensorDto {
    @SerializedName("id")
    private int id;

    @SerializedName("chipId")
    private String chipId;

    // сервер віддає "RoomName"
    @SerializedName("roomName")
    private String roomName;

    @SerializedName("imageName")
    private String imageName;

    // сервер віддає Double? (може бути null)
    @SerializedName("temperature")
    private Double temperature;

    @SerializedName("humidity")
    private Double humidity;

    public RoomWithSensorDto() {}

    public RoomWithSensorDto(int id, String chipId, String roomName, String imageName, Double temperature, Double humidity) {
        this.id = id;
        this.chipId = chipId;
        this.roomName = roomName;
        this.imageName = imageName;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    // getters
    public int getId() { return id; }
    public String getChipId() { return chipId; }
    public String getRoomName() { return roomName; }
    public String getImageName() { return imageName; }
    public Double getTemperature() { return temperature; }
    public Double getHumidity() { return humidity; }

    // setters (якщо треба)
    public void setId(int id) { this.id = id; }
    public void setChipId(String chipId) { this.chipId = chipId; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
}
