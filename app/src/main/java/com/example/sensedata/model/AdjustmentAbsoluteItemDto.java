// ---- POST (відправка абсолютних значень) ----
package com.example.sensedata.model;

public class AdjustmentAbsoluteItemDto {
    public String parameterName;
    public Float low;
    public Float high;

    public AdjustmentAbsoluteItemDto(String parameterName, Float low, Float high) {
        this.parameterName = parameterName;
        this.low = low;
        this.high = high;
    }
}
