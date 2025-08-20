package com.example.sensedata.network;

import com.example.sensedata.model.RoomWithSensorDto;
import com.example.sensedata.model.SensorOwnershipCreateDto;
import com.example.sensedata.model.SensorOwnershipUpdateDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface RoomApiService {

    // POST: створити ownership — сервер тепер повертає RoomWithSensorDto (201 Created)
    @POST("DisplayData/ownership")
    Call<RoomWithSensorDto> createRoom(@Body SensorOwnershipCreateDto request);

    // GET: отримати кімнату по chipId+userId
    @GET("DisplayData/ownership/{chipId}/user/{userId}/latest")
    Call<RoomWithSensorDto> getRoomByChipId(
            @Path("chipId") String chipId,
            @Path("userId") int userId
    );

    // GET: всі кімнати користувача
    @GET("DisplayData/byUser/{userId}")
    Call<List<RoomWithSensorDto>> getAllRooms(@Path("userId") int userId);

    @PUT("sensordata/ownership")
    Call<Void> updateOwnership(@Body SensorOwnershipUpdateDto body);

    // DELETE /ownership/{chipId}/user/{userId}
    @retrofit2.http.DELETE("sensordata/ownership/{chipId}/user/{userId}")
    Call<Void> deleteOwnership(
            @retrofit2.http.Path("chipId") String chipId,
            @retrofit2.http.Path("userId") int userId
    );

}
