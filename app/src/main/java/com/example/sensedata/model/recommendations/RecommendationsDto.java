package com.example.sensedata.model.recommendations;

import com.example.sensedata.model.sensordata.SensorDataDto;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RecommendationsDto {
    @SerializedName("data")   public SensorDataDto data;
    @SerializedName("advice") public List<String> advice;
}
