package com.example.sensedata.model.sensorownership;

import com.google.gson.annotations.SerializedName;

public class SensorOwnershipCreateDto {
    @SerializedName("userId")   public int userId;
    @SerializedName("chipId")   public String chipId;
    @SerializedName("roomName") public String roomName;
    @SerializedName("imageName") public String imageName;

    public SensorOwnershipCreateDto(int userId, String chipId, String roomName, String imageName) {
        this.userId = userId;
        this.chipId = chipId;
        this.roomName = roomName;
        this.imageName = imageName;
    }
}
