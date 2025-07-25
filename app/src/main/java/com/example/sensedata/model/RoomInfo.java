package com.example.sensedata.model;

public class RoomInfo {
    public String name;
    public int imageResId;
    public String temperature;
    public String humidity;

    public RoomInfo(String name, int imageResId, String temperature, String humidity) {
        this.name = name;
        this.imageResId = imageResId;
        this.temperature = temperature;
        this.humidity = humidity;
    }
}

