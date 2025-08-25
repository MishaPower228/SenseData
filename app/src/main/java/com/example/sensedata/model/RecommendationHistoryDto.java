package com.example.sensedata.model;

import com.google.gson.annotations.SerializedName;

public class RecommendationHistoryDto {
    @SerializedName("createdAt")     public String createdAt;   // ISO 8601 з сервера
    @SerializedName("roomName")      public String roomName;
    @SerializedName("recommendation")public String recommendation; // рядки через \n
}

