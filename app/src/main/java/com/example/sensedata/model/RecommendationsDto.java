package com.example.sensedata.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RecommendationsDto {

    @SerializedName("data")
    public DataPayload data;

    @SerializedName("advice")
    public List<String> advice;

    public static class DataPayload {
        @SerializedName("chipId") public String chipId;
        @SerializedName("roomName") public String roomName;

        @SerializedName("TemperatureDht") public Float temperatureDht;
        @SerializedName("HumidityDht")    public Float humidityDht;
        @SerializedName("Pressure")       public Float pressure;
        @SerializedName("Altitude")       public Float altitude;
        @SerializedName("TemperatureBme") public Float temperatureBme;
        @SerializedName("HumidityBme")    public Float humidityBme;

        @SerializedName("GasDetected")    public Boolean gasDetected;
        @SerializedName("Light")          public Boolean light;

        @SerializedName("MQ2Analog")         public Integer mq2Analog;
        @SerializedName("MQ2AnalogPercent")  public Float mq2AnalogPercent;
        @SerializedName("LightAnalog")       public Integer lightAnalog;
        @SerializedName("LightAnalogPercent")public Float lightAnalogPercent;
    }
}
