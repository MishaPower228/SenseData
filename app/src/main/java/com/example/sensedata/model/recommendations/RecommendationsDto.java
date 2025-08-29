package com.example.sensedata.model.recommendations;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RecommendationsDto {
    @SerializedName("advice") public List<String> advice;
}
