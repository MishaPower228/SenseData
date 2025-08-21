package com.example.sensedata.model;

import com.google.gson.annotations.SerializedName;

public class SensorDataDTO {

    @SerializedName("chipId") public String chipId;
    @SerializedName("roomName") public String roomName;
    @SerializedName("temperatureDht") public Float temperatureDht;
    @SerializedName("humidityDht")    public Float humidityDht;
    @SerializedName("gasDetected")    public Boolean gasDetected;
    @SerializedName("light")          public Boolean light;

    @SerializedName("pressure")       public Float pressure;
    @SerializedName("altitude")       public Float altitude;

    @SerializedName("temperatureBme") public Float temperatureBme;
    @SerializedName("humidityBme")    public Float humidityBme;

    @SerializedName("mq2Analog")         public Integer mq2Analog;
    @SerializedName("mq2AnalogPercent")  public Float   mq2AnalogPercent;
    @SerializedName("lightAnalog")       public Integer lightAnalog;
    @SerializedName("lightAnalogPercent")public Float   lightAnalogPercent;

}
