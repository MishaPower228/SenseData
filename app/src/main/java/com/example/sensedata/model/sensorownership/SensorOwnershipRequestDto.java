package com.example.sensedata.model.sensorownership;

import com.google.gson.annotations.SerializedName;

public class SensorOwnershipRequestDto {
    @SerializedName("roomName")  public String roomName;
    @SerializedName("imageName") public String imageName;
    @SerializedName("chipId")    public String chipId;
    @SerializedName("username")  public String username;

    public SensorOwnershipRequestDto(String roomName, String imageName, String chipId, String username) {
        this.roomName  = roomName;
        this.imageName = imageName;
        this.chipId    = chipId;
        this.username  = username;
    }
}
