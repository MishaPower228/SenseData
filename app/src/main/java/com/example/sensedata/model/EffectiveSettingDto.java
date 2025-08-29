package com.example.sensedata.model;

public class EffectiveSettingDto {
    public String parameterName;
    public Float lowValue;
    public Float highValue;
    public String lowValueMessage;
    public String highValueMessage;

    public String getParameterName() { return parameterName; }
    public Float getLowValue() { return lowValue; }
    public Float getHighValue() { return highValue; }
    public String getLowValueMessage() { return lowValueMessage; }
    public String getHighValueMessage() { return highValueMessage; }
}
