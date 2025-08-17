package com.example.sensedata.model;

public class RoomWithSensorDto {
    private int id;
    private String chipId;
    private String name;
    private String imageName;
    private String temperature;
    private String humidity;

    // Повний конструктор
    public RoomWithSensorDto(int id, String chipId, String name, String imageName, String temperature, String humidity) {
        this.id = id;
        this.chipId = chipId;
        this.name = name;
        this.imageName = imageName;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    // Конструктор тільки з ім’ям кімнати
    public RoomWithSensorDto(String name) {
        this.id = 0;
        this.name = name;
        this.imageName = "";
        this.temperature = "";
        this.humidity = "";
    }

    // Геттери
    public int getId() {
        return id;
    }

    public String getChipId() {
        return chipId;
    }

    public String getRoomName() {
        return name;
    }

    public String getImageName() {
        return imageName;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    // Сеттери (якщо потрібно)
    public void setId(int id) {
        this.id = id;
    }

    public void setRoomName(String name) {
        this.name = name;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }

    public void setHumidity(String humidity) {
        this.humidity = humidity;
    }
}
