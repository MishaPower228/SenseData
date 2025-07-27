// RoomApiService.java
package com.example.sensedata.network;

import com.example.sensedata.model.RoomRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface RoomApiService {
    @POST("api/rooms")
    Call<Void> createRoom(@Body RoomRequest room);
}
