package com.example.sensedata.network;

import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipRequestDTO;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RoomApiService {

    // POST: створити кімнату (без тіла у відповіді)
    @POST("DisplayData/ownership")
    Call<Void> createRoom(@Body SensorOwnershipRequestDTO request);

    // GET: отримати кімнату по chipId
    @GET("/ownership/{chipId}/user/{userId}/latest")
    Call<RoomWithSensorDto> getRoomByChipId(
            @Path("chipId") String chipId,
            @Path("userId") int userId
    );

    // GET: отримати всі кімнати користувача
    @GET("DisplayData/byUser/{userId}")
    Call<List<RoomWithSensorDto>> getAllRooms(@Path("userId") int userId);
}
