package com.example.sensedata.model.room;

import com.google.gson.annotations.SerializedName;

public class RoomRequest {
    @SerializedName("name")      public String name;
    @SerializedName("imageName") public String imageName;
    @SerializedName("username")  public String username;

    public RoomRequest(String name, String imageName, String username) {
        this.name = name;
        this.imageName = imageName;
        this.username = username;
    }
}
