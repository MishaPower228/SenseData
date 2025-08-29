package com.example.sensedata.model.user;

import com.google.gson.annotations.SerializedName;

public class RefreshRequest {
    @SerializedName("refreshToken") public String refreshToken;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
