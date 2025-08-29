package com.example.sensedata.model.adjustment;

import com.google.gson.annotations.SerializedName;

public class EffectiveSettingDto {
    @SerializedName("parameterName")   public String parameterName;
    @SerializedName("lowValue")        public Float lowValue;         // може бути null
    @SerializedName("highValue")       public Float highValue;        // може бути null
    @SerializedName("lowValueMessage") public String lowValueMessage; // може бути null
    @SerializedName("highValueMessage")public String highValueMessage;// може бути null

    public String getParameterName() { return parameterName; }
    public Float getLowValue() { return lowValue; }
    public Float getHighValue() { return highValue; }
    public String getLowValueMessage() { return lowValueMessage; }
    public String getHighValueMessage() { return highValueMessage; }
}