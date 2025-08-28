package com.example.sensedata.network;

import com.example.sensedata.model.LoginRequest;
import com.example.sensedata.model.RefreshRequest;
import com.example.sensedata.model.RegisterRequest;
import com.example.sensedata.model.UserProfileResponse;
import com.example.sensedata.model.UserResponse;
import com.example.sensedata.model.UserLoginDto;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface UserApiService {
    @POST("Users/register")
    Call<UserResponse> register(@Body RegisterRequest request);

    @POST("Users/login")
    Call<UserResponse> login(@Body LoginRequest request);

    @POST("Users/refresh")
    Call<UserResponse> refreshToken(@Body RefreshRequest request);

    @GET("Users/{id}")
    Call<String> getUsername(@Path("id") int id);

    @GET("Users/{id}/profile")
    Call<UserProfileResponse> getUserProfile(@Path("id") int userId);
}
