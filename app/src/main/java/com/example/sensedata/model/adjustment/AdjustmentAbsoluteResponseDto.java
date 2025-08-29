package com.example.sensedata.model.adjustment;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AdjustmentAbsoluteResponseDto {
    @SerializedName("userId") public int userId;
    @SerializedName("items")  public List<EffectiveSettingDto> items;
}
