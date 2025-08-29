package com.example.sensedata.model.recommendations;

import com.google.gson.annotations.SerializedName;

public class SaveLatestRecommendationDto {
    @SerializedName("saved") public boolean saved;
    @SerializedName("count") public int count;
}
