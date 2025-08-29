package com.example.sensedata.model.user;

import com.google.gson.annotations.SerializedName;

public class UserResponse {
    @SerializedName("id")           public int id;
    @SerializedName("username")     public String username;
    @SerializedName("email")        public String email;
    @SerializedName("roleName")     public String roleName;
    @SerializedName("accessToken")  public String accessToken;
    @SerializedName("refreshToken") public String refreshToken;

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRoleName() { return roleName; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
}
