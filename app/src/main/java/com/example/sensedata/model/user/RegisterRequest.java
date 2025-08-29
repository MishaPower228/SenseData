package com.example.sensedata.model.user;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {
    @SerializedName("username") public String username;
    @SerializedName("email")    public String email;
    @SerializedName("password") public String password;

    public RegisterRequest(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }
}
