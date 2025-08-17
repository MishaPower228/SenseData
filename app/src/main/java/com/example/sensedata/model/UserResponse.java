package com.example.sensedata.model;

public class UserResponse {
    private int id;
    private String username;
    private String email;
    private String roleName;
    private String accessToken;
    private String refreshToken;

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRoleName() { return roleName; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
}

