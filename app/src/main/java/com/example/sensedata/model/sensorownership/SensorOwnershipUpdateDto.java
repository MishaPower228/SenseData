package com.example.sensedata.model.sensorownership;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public class SensorOwnershipUpdateDto {
    @SerializedName("chipId")    public String chipId;
    @SerializedName("roomName")  public String roomName;
    @SerializedName("imageName") public String imageName;

    public SensorOwnershipUpdateDto(String chipId, String roomName, String imageName) {
        this.chipId    = chipId == null ? null : chipId.trim().toUpperCase(Locale.ROOT);
        this.roomName  = roomName;
        this.imageName = imageName;
    }
}
