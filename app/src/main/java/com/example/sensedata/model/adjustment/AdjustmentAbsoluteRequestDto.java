package com.example.sensedata.model.adjustment;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class AdjustmentAbsoluteRequestDto {
    @SerializedName("items") public List<Item> items;

    public AdjustmentAbsoluteRequestDto(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        @SerializedName("parameterName") public String parameterName;
        @SerializedName("low")           public Float low;
        @SerializedName("high")          public Float high;

        public Item(String parameterName, Float low, Float high) {
            this.parameterName = parameterName;
            this.low = low;
            this.high = high;
        }
    }
}
