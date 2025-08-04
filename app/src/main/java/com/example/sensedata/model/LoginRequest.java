package com.example.sensedata.model;

public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest(String login, String password) {
        this.username = login;
        this.password = password;
    }

    public String getLogin() { return username; }
    public String getPassword() { return password; }
}
