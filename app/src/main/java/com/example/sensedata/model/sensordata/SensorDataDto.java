package com.example.sensedata.model.sensordata;

import com.google.gson.annotations.SerializedName;

public class SensorDataDto {

    @SerializedName("chipId")
    public String chipId;

    @SerializedName(value = "roomName", alternate = {"RoomName"})
    public String roomName;

    @SerializedName(value = "temperatureDht", alternate = {"TemperatureDht"})
    public Float temperatureDht;

    @SerializedName(value = "humidityDht",   alternate = {"HumidityDht"})
    public Float humidityDht;

    @SerializedName(value = "gasDetected",   alternate = {"GasDetected"})
    public Boolean gasDetected;

    @SerializedName(value = "light",         alternate = {"Light"})
    public Boolean light;

    @SerializedName(value = "pressure",      alternate = {"Pressure"})
    public Float pressure;

    @SerializedName(value = "altitude",      alternate = {"Altitude"})
    public Float altitude;

    @SerializedName(value = "temperatureBme", alternate = {"TemperatureBme"})
    public Float temperatureBme;

    @SerializedName(value = "humidityBme",    alternate = {"HumidityBme"})
    public Float humidityBme;

    @SerializedName(value = "mq2Analog",        alternate = {"MQ2Analog"})
    public Integer mq2Analog;

    @SerializedName(value = "mq2AnalogPercent", alternate = {"MQ2AnalogPercent"})
    public Float mq2AnalogPercent;

    @SerializedName(value = "lightAnalog",       alternate = {"LightAnalog"})
    public Integer lightAnalog;

    @SerializedName(value = "lightAnalogPercent", alternate = {"LightAnalogPercent"})
    public Float lightAnalogPercent;
}
