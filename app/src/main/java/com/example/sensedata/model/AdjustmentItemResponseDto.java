// ---- GET (отримання останніх налаштувань) ----
package com.example.sensedata.model;

public class AdjustmentItemResponseDto {
    public String parameterName;
    public float lowValueAdjustment;
    public float highValueAdjustment;
    public int version;

    public String getParameterName() { return parameterName; }
    public float getLowValueAdjustment() { return lowValueAdjustment; }
    public float getHighValueAdjustment() { return highValueAdjustment; }
    public int getVersion() { return version; }
}
