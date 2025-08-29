package com.example.sensedata.model.user;

import com.google.gson.annotations.SerializedName;

public class UserProfileDto {
    @SerializedName("id")       public int id;
    @SerializedName("username") public String username;
    @SerializedName("email")    public String email;
    @SerializedName("roleName") public String roleName; // може бути null

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRoleName() { return roleName; }
}
