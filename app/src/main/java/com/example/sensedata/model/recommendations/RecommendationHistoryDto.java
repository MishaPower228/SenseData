package com.example.sensedata.model.recommendations;

import com.google.gson.annotations.SerializedName;

public class RecommendationHistoryDto {
    @SerializedName("createdAt")     public String createdAt;     // ISO8601 з сервера
    @SerializedName("roomName")      public String roomName;
    @SerializedName("recommendation")public String recommendation; // рядки через \n
}
