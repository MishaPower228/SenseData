package com.example.sensedata.model;

public class RoomWithSensorDto {
    public int id;
    public String name;
    public String imageName;
    public String temperature;
    public String humidity;

    // Повний конструктор
    public RoomWithSensorDto(int id, String name, String imageName, String temperature, String humidity) {
        this.id = id;
        this.name = name;
        this.imageName = imageName;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    // ✅ Додатковий конструктор для простого створення об'єкта лише з ім’ям кімнати
    public RoomWithSensorDto(String name) {
        this.id = 0;
        this.name = name;
        this.imageName = "";
        this.temperature = "";
        this.humidity = "";
    }
}
