package com.example.sensedata.model;

import java.util.List;

public class AdjustmentAbsoluteRequestDto {
    public List<AdjustmentAbsoluteItemDto> items;

    public AdjustmentAbsoluteRequestDto(List<AdjustmentAbsoluteItemDto> items) {
        this.items = items;
    }
}
