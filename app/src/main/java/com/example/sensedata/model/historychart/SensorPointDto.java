package com.example.sensedata.model.historychart;

import com.google.gson.annotations.SerializedName;

public class SensorPointDto {
    @SerializedName("timestampUtc") public String timestampUtc; // "2025-08-26T10:00:00Z"
    @SerializedName("temperature")  public Integer temperature;  // за потреби можна змінити на Float
    @SerializedName("humidity")     public Integer humidity;
}
