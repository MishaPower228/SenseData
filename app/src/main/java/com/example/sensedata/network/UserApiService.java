package com.example.sensedata.network;

import com.example.sensedata.model.user.LoginRequest;
import com.example.sensedata.model.user.RefreshRequest;
import com.example.sensedata.model.user.RegisterRequest;
import com.example.sensedata.model.user.UserProfileDto;
import com.example.sensedata.model.user.UserResponse;

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
    Call<UserProfileDto> getUserProfile(@Path("id") int userId);
}
